package io.github.andrewlalis.sample_lucene_search;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AirportParser {
	private AirportParser() {}

	public static List<Airport> parseAirports(Path filePath) {
		CSVFormat format = CSVFormat.DEFAULT.builder()
				.setHeader()
				.setSkipHeaderRecord(true)
				.build();
		try (
				var reader = Files.newBufferedReader(filePath);
				var parser = format.parse(reader)
		) {
			var it = parser.iterator();
			List<Airport> airports = new ArrayList<>();
			while (it.hasNext()) {
				airports.add(parseAirport(it.next()));
			}
			return airports;
		} catch (IOException e) {
			System.err.println("Error reading airports.");
			e.printStackTrace();
			return new ArrayList<>();
		}
	}

	private static Airport parseAirport(CSVRecord r) {
		return new Airport(
				Long.parseLong(r.get("id")),
				r.get("ident"),
				r.get("type"),
				r.get("name"),
				Double.parseDouble(r.get("latitude_deg")),
				Double.parseDouble(r.get("longitude_deg")),
				getOptionalString(r, "elevation_ft").map(Integer::parseInt),
				r.get("continent"),
				r.get("iso_country"),
				r.get("iso_region"),
				r.get("municipality"),
				r.get("scheduled_service").equalsIgnoreCase("yes"),
				getOptionalString(r, "gps_code"),
				getOptionalString(r, "iata_code"),
				getOptionalString(r, "local_code"),
				getOptionalString(r, "home_link"),
				getOptionalString(r, "wikipedia_link"),
				getOptionalString(r, "keywords")
		);
	}

	private static Optional<String> getOptionalString(CSVRecord r, String key) {
		String value = r.get(key);
		if (value.isBlank()) value = null;
		return Optional.ofNullable(value);
	}
}
