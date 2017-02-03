package ivb;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * IVB request class
 * 
 * @author phoenix
 *
 */
public class IVB {

	private final int CACHE_EMPTY = 0;
	private final int CACHE_FRESH = 1;
	private final int CACHE_DIRTY = 2;

	/** A departure entry */
	public class Departure {
		public final String line;
		public final String direction;
		public final String time;

		protected Departure(String line, String direction, String time) {
			this.line = line;
			this.direction = direction;
			this.time = time;
		}

		@Override
		public String toString() {
			return line + "\t" + direction + "\t\t" + time;
		}
	}

	public class Station {
		public final String name;

		/** Value for a request */
		protected final String value;

		protected Station(String name, String value) {
			super();
			this.name = name;
			this.value = value;
		}

		@Override
		public String toString() {
			return name;
		}

		@Override
		public int hashCode() {
			return name.hashCode();
		}
	}

	private static final String SMARTONLINE_LINK = "http://www.ivb.at/de/services/smartinfo/smartinfoonline.html";
	private static final String DEFAULT_CHARSET = "Windows-1252";

	/** Cache status of the stations. 0 = Emtpy, 1 = Fresh, 2 = Dirty */
	private int iCacheStations = CACHE_EMPTY;

	private final Map<String, Station> stations = new HashMap<String, IVB.Station>();

	/**
	 * Get all stops
	 * 
	 * @return all stops that are available
	 * @throws IOException
	 *             Thrown if occurring while loading data
	 */
	public List<Station> getStations() throws IOException {
		synchronized (this.stations) {

			if (iCacheStations != CACHE_FRESH) {
				final URL url = new URL(SMARTONLINE_LINK);
				final InputStream in = url.openStream();
				Document doc = Jsoup.parse(in, DEFAULT_CHARSET, SMARTONLINE_LINK);
				try {
					this.stations.clear();

					final Elements smartinfoformular = doc.getElementsByAttributeValue("id", "smartinfoformular"); // smartinfoformular
					if (smartinfoformular.isEmpty())
						throw new IOException("Illegal response - smatinfoformulat element not found");

					// Find select item
					final int count = smartinfoformular.size();
					for (int i = 0; i < count; i++) {
						final Elements current = smartinfoformular.get(i).getElementsByTag("select");
						if (current.size() > 0) {
							// Select all options
							final Elements options = current.get(0).getElementsByTag("option");

							// Extract all of them
							for (final Element element : options) {
								final Station station = new Station(element.text(), element.attr("value"));
								this.stations.put(station.name, station);
							}
						}
					}
					this.iCacheStations = CACHE_FRESH;
				} finally {
					in.close();
				}
			}

			final List<Station> ret = new ArrayList<>(this.stations.values());
			java.util.Collections.sort(ret, new Comparator<Station>() {

				@Override
				public int compare(Station o1, Station o2) {
					return o1.name.compareTo(o2.name);
				}
			});
			return ret;
		}
	}

	/**
	 * Get all stations in the background. Successive calls accessing the
	 * stations will be blocked, until this call is terminated
	 * 
	 * @return all stops that are available
	 * @throws IOException
	 *             Thrown if occurring while loading data
	 */
	public Future<List<Station>> fetchStationsBackground() throws IOException {
		final FutureTask<List<Station>> task = new FutureTask<List<Station>>(new Callable<List<Station>>() {

			@Override
			public List<Station> call() throws Exception {
				IVB.this.clearStationsCache();
				return IVB.this.getStations();
			}
		});

		return task;
	}

	/**
	 * Get station with the given name or null, if not existing. If the stations
	 * are not fetched yet, they will be fetched
	 * 
	 * @param station
	 *            Station name to be searched
	 * @return {@link Station} instance or null, if not existing
	 * @throws IOException
	 *             Thrown if occurring while fetching all stations
	 */
	public Station getStation(final String station) throws IOException {
		if (station == null || station.trim().isEmpty())
			return null;

		synchronized (this.stations) {
			if (this.stations.isEmpty())
				getStations();
			return this.stations.get(station);
		}
	}

	public List<Departure> getDepartures(final Station station) throws IOException {
		return getDepartures(station, 8);
	}

	public List<Departure> getDepartures(final Station station, final int rows) throws IOException {
		Date date = new Date(); // given date
		Calendar calendar = GregorianCalendar.getInstance();
		calendar.setTime(date); // assigns calendar to given date
		final int hour = calendar.get(Calendar.HOUR_OF_DAY); // gets hour in 24h
		final int minute = calendar.get(Calendar.MINUTE);

		String link = "http://www.ivb.at/index.php?id=276&L=0&si[stopsearch]=" + station.value + "&si[stopid]="
				+ station.value + "&si[route]=&si[opttime]=now&si[hour]=" + hour + "&si[minute]=" + minute
				+ "&si[nrows]=" + rows;

		final URL url = new URL(link);
		final InputStream in = url.openStream();
		try {

			final Document doc = Jsoup.parse(in, DEFAULT_CHARSET, link);

			final Elements elements = doc.getElementsByClass("echtzeitzeile");

			final List<Departure> ret = new ArrayList<>(elements.size());
			int id = 0;
			for (final Element element : elements) {
				try {

					final String line = element.getElementById("siroute_" + id).text();
					final String dir = element.getElementById("sidir_" + id).text();
					final String time = element.getElementById("sitime_" + id).text();

					ret.add(new Departure(line, dir, time));

				} catch (NumberFormatException e) {
					continue;
				} catch (NullPointerException e) {
					continue;
				}

				id++;
			}
			return ret;

		} finally {
			in.close();
		}
	}

	/**
	 * Clear the stations cache
	 */
	public void clearStationsCache() {
		this.stations.clear();
		iCacheStations = CACHE_EMPTY;
	}

	/**
	 * This call will trigger a station cache refresh the next time is is
	 * accessed
	 */
	public void setStationCacheDirty() {
		this.iCacheStations = CACHE_DIRTY;
	}

	/**
	 * Reads contents from the given cache file
	 * 
	 * @param filename
	 *            Filename of the cache file to write to
	 * @throws IOException
	 *             Thrown if occurring while reading from disk
	 */
	public void readCacheFile(final String filename) throws IOException {
		final FileInputStream in = new FileInputStream(filename);
		try {
			final Scanner scanner = new Scanner(in);

			int iSection = 0; // Section index
			/*
			 * Section indices: 0 = Unknwon 1 = Stations
			 */

			// Read stations
			int lineCount = 0;
			while (scanner.hasNextLine()) {
				final String line = scanner.nextLine().trim();
				lineCount++;
				if (line.isEmpty())
					continue;
				if (line.charAt(0) == '#')
					continue; // Ignore comments

				// Read section name
				if (line.startsWith("[") && line.endsWith("]")) {
					if (line.length() == 2)
						continue;
					String name = line.substring(1, line.length() - 1);

					if (name.equalsIgnoreCase("stations"))
						iSection = 1;
					else
						System.err.println("Cache file: Line " + lineCount + " - Unknwon section");
				} else {
					switch (iSection) {
					case 1: // Station
						String[] split = line.split("=", 2);
						if (split.length < 2) {
							System.err.println("Cache file: Line " + lineCount + " - Illegal format");
						} else {
							final String name = split[0];
							final String value = split[1];

							final Station station = new Station(name, value);
							this.stations.put(name, station);
						}
						break;
					default:
						// Something's wrong
						System.err.println("Cache file: Line " + lineCount + " - Wised line");
						continue;
					}
				}
			}

			if (this.stations.size() > 0)
				this.iCacheStations = CACHE_FRESH;

			scanner.close();
		} finally {
			in.close();
		}
	}

	/**
	 * Write cache to disk
	 * 
	 * @param filename
	 *            Filename of the cache file to write to
	 * @throws IOException
	 *             Thrown if occurring while writing to disk
	 */
	public void writeCacheFile(final String filename) throws IOException {
		final FileOutputStream out = new FileOutputStream(filename, false);
		final PrintWriter writer = new PrintWriter(out);
		try {

			writer.println(
					"# Automatically generated cache file. Do not edit manually, because the contents will be overwritten");
			writer.println("");

			// Write stations
			writer.println("[Stations]");
			for (final Station station : getStations())
				writer.println(station.name + "=" + station.value);

		} finally {
			writer.close();
			out.close();
		}
	}

}
