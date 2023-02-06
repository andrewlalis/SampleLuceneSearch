package io.github.andrewlalis.sample_lucene_search;

import java.util.Optional;

public record Airport(
		long id,
		String ident,
		String type,
		String name,
		double latitude,
		double longitude,
		Optional<Integer> elevationFt,
		String continent,
		String isoCountry,
		String isoRegion,
		String municipality,
		boolean scheduledService,
		Optional<String> gpsCode,
		Optional<String> iataCode,
		Optional<String> localCode,
		Optional<String> homeLink,
		Optional<String> wikipediaLink,
		Optional<String> keywords
) {}
