package ivb;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
			final URL url = new URL(SMARTONLINE_LINK);
			final InputStream in = url.openStream();
			Document doc = Jsoup.parse(in, DEFAULT_CHARSET, SMARTONLINE_LINK);
			try {
				this.stations.clear();

				final Elements smartinfoformular = doc
						.getElementsByAttributeValue("id", "smartinfoformular"); // smartinfoformular
				if (smartinfoformular.isEmpty())
					throw new IOException(
							"Illegal response - smatinfoformulat element not found");

				// Find select item
				final int count = smartinfoformular.size();
				for (int i = 0; i < count; i++) {
					final Elements current = smartinfoformular.get(i)
							.getElementsByTag("select");
					if (current.size() > 0) {
						// Select all options
						final Elements options = current.get(0)
								.getElementsByTag("option");

						// Extract all of them
						for (final Element element : options) {
							final Station station = new Station(element.text(),
									element.attr("value"));
							this.stations.put(station.name, station);
						}
					}
				}

				final List<Station> ret = new ArrayList<>(
						this.stations.values());
				java.util.Collections.sort(ret, new Comparator<Station>() {

					@Override
					public int compare(Station o1, Station o2) {
						return o1.name.compareTo(o2.name);
					}
				});
				return ret;
			} finally {
				in.close();
			}
		}
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

	public List<Departure> getDepartures(final Station station)
			throws IOException {
		return getDepartures(station, 8);
	}

	public List<Departure> getDepartures(final Station station, final int rows)
			throws IOException {
		Date date = new Date(); // given date
		Calendar calendar = GregorianCalendar.getInstance();
		calendar.setTime(date); // assigns calendar to given date
		final int hour = calendar.get(Calendar.HOUR_OF_DAY); // gets hour in 24h
		final int minute = calendar.get(Calendar.MINUTE);

		String link = "http://www.ivb.at/index.php?id=276&L=0&si[stopsearch]="
				+ station.value + "&si[stopid]=" + station.value
				+ "&si[route]=&si[opttime]=now&si[hour]=" + hour
				+ "&si[minute]=" + minute + "&si[nrows]=" + rows;

		final URL url = new URL(link);
		final InputStream in = url.openStream();
		try {

			final Document doc = Jsoup.parse(in, DEFAULT_CHARSET, link);

			final Elements elements = doc.getElementsByClass("echtzeitzeile");

			final List<Departure> ret = new ArrayList<>(elements.size());
			int id = 0;
			for (final Element element : elements) {
				try {

					final String line = element.getElementById("siroute_" + id)
							.text();
					final String dir = element.getElementById("sidir_" + id)
							.text();
					final String time = element.getElementById("sitime_" + id)
							.text();

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

	public static void main(final String[] args) throws IOException {
		if (args.length == 0) {
			System.out.println("Chose a station for the departures");
			System.out
					.println("Type --stations to list all available stations");
			System.exit(1);
		}

		final IVB ivb = new IVB();
		// TODO: Cache stations in local file
		ivb.getStations();

		for (final String arg : args) {
			if (arg.equals("--stations")) {
				// List stations

				for (final Station station : ivb.getStations())
					System.out.println(station);

			} else if (arg.startsWith("-")) {
				System.err.println("Illegal argument: " + arg);
				System.exit(1);
			} else {
				final Station station = ivb.getStation(arg);
				if (station == null) {
					System.err.println("Error - Station not found: \"" + arg
							+ "\"");
				} else {
					for (final Departure departure : ivb.getDepartures(station)) {
						System.out.println(departure);
					}
				}
			}
		}
	}
}
