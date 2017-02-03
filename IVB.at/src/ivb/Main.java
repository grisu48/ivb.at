package ivb;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import ivb.IVB.Departure;
import ivb.IVB.Station;

public class Main {

	private static String CACHE_FILE = "";

	public static void main(final String[] args) throws IOException {
		if (args.length == 0) {
			System.out.println("Chose a station for the departures");
			System.out.println("Type --stations to list all available stations");
			System.exit(1);
		}

		final IVB ivb = new IVB();

		String userHome = System.getProperty("user.home");
		if (userHome.isEmpty()) {
			System.err.println("Warning: Cannot determine user's home directory");
			CACHE_FILE = null;
		} else {
			if (!userHome.endsWith(File.separator))
				userHome += File.separator;
			CACHE_FILE = userHome + ".ivb.cache";
		}

		// Load local cache file
		boolean writeCache = !loadCache(ivb);

		for (final String arg : args) {
			if (arg.equals("--stations")) {
				// List stations

				for (final Station station : ivb.getStations())
					System.out.println(station);
			} else if (arg.equals("--refresh")) {
				ivb.fetchStationsBackground();
				writeCache = true;
			} else if (arg.startsWith("-")) {
				System.err.println("Illegal argument: " + arg);
				System.exit(1);
			} else {
				final Station station = ivb.getStation(arg);
				if (station == null) {
					System.err.println("Error - Station not found: \"" + arg + "\"");
				} else {
					for (final Departure departure : ivb.getDepartures(station)) {
						System.out.println(departure);
					}
				}
			}
		}

		// Write cache to file, if it has changed
		if (writeCache)
			ivb.writeCacheFile(CACHE_FILE);
	}

	/**
	 * Load cache
	 * 
	 * @param ivb
	 *            where to load into
	 * @return true if loaded, false if not loaded
	 */
	private static boolean loadCache(final IVB ivb) {
		if (CACHE_FILE == null)
			return false;

		try {

			ivb.readCacheFile(CACHE_FILE);

			return true;
		} catch (FileNotFoundException e) {
			return false;
		} catch (IOException e) {
			System.err.println("Error reading cache file: " + e.getMessage());
			return false;
		}
	}
}
