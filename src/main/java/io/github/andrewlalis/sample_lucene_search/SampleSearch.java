package io.github.andrewlalis.sample_lucene_search;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sample application that showcases the most basic way in which Apache Lucene
 * can be used to index and search large datasets.
 */
public class SampleSearch {
	public static void main(String[] args) throws IOException {
		List<Airport> airports = AirportParser.parseAirports(Path.of("airports.csv"));
		System.out.println("Read " + airports.size() + " airports.");

		buildIndex(airports);
		System.out.println("Built index.");

		System.out.println("Entering search-cli mode. Type a query. Type \"exit\" to quit.");
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		String line;
		while ((line = reader.readLine()) != null) {
			String rawQuery = line.strip().toLowerCase();
			if (rawQuery.equals("exit")) break;
			var results = searchAirports(rawQuery);
			int i = 1;
			for (var name : results) {
				System.out.println("  " + i++ + ". " + name);
			}
		}
		System.out.println("Done!");
	}

	/**
	 * Constructs an index from a list of airports.
	 * @param airports The airports to index.
	 * @throws IOException If an error occurs.
	 */
	public static void buildIndex(List<Airport> airports) throws IOException {
		Path indexDir = Path.of("airports-index");
		// We use a try-with-resources block to prepare the components needed for writing the index.
		try (
			Analyzer analyzer = new StandardAnalyzer();
			Directory luceneDir = FSDirectory.open(indexDir)
		) {
			IndexWriterConfig config = new IndexWriterConfig(analyzer);
			config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
			IndexWriter indexWriter = new IndexWriter(luceneDir, config);
			for (var airport : airports) {
				// Create a new document for each airport.
				Document doc = new Document();
				doc.add(new StoredField("id", airport.id()));
				doc.add(new TextField("ident", airport.ident(), Field.Store.YES));
				doc.add(new TextField("type", airport.type(), Field.Store.YES));
				doc.add(new TextField("name", airport.name(), Field.Store.YES));
				doc.add(new TextField("continent", airport.continent(), Field.Store.YES));
				doc.add(new TextField("isoCountry", airport.isoCountry(), Field.Store.YES));
				doc.add(new TextField("municipality", airport.municipality(), Field.Store.YES));
				doc.add(new IntPoint("elevationFt", airport.elevationFt().orElse(0)));
				doc.add(new StoredField("elevationFt", airport.elevationFt().orElse(0)));
				if (airport.wikipediaLink().isPresent()) {
					doc.add(new StoredField("wikipediaLink", airport.wikipediaLink().get()));
				}
				// And add it to the writer.
				indexWriter.addDocument(doc);
			}
			indexWriter.close();
		}
	}

	/**
	 * Searches over an index to find the names of airports matching the given
	 * textual query.
	 * @param rawQuery The raw textual query entered by a human.
	 * @return A list of airport names.
	 */
	public static List<String> searchAirports(String rawQuery) {
		Path indexDir = Path.of("airports-index");
		// If the query is empty or there's no index, quit right away.
		if (rawQuery == null || rawQuery.isBlank() || Files.notExists(indexDir)) return new ArrayList<>();

		// Prepare a weight for each of the fields we want to search on.
		Map<String, Float> fieldWeights = Map.of(
				"name", 3f,
				"municipality", 2f,
				"ident", 2f,
				"type", 1f,
				"continent", 0.25f
		);

		// Build a boolean query made up of "boosted" wildcard term queries, that'll match any term.
		BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
		String[] terms = rawQuery.toLowerCase().split("\\s+");
		for (String term : terms) {
			// Make the term into a wildcard term, where we match any field value starting with the given text.
			// For example, "airp*" will match "airport" and "airplane", but not "airshow".
			// This is usually the natural way in which people like to search.
			String wildcardTerm = term + "*";
			for (var entry : fieldWeights.entrySet()) {
				String fieldName = entry.getKey();
				float weight = entry.getValue();
				Query baseQuery = new WildcardQuery(new Term(fieldName, wildcardTerm));
				queryBuilder.add(new BoostQuery(baseQuery, weight), BooleanClause.Occur.SHOULD);
			}
		}
		Query query = queryBuilder.build();

		// Use the query we built to fetch up to 10 results.
		try (var reader = DirectoryReader.open(FSDirectory.open(indexDir))) {
			IndexSearcher searcher = new IndexSearcher(reader);
			List<String> results = new ArrayList<>(10);
			TopDocs topDocs = searcher.search(query, 10, Sort.RELEVANCE, false);
			for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
				Document doc = searcher.storedFields().document(scoreDoc.doc);
				results.add(doc.get("name"));
			}
			return results;
		} catch (IOException e) {
			System.err.println("Failed to search index.");
			e.printStackTrace();
			return new ArrayList<>();
		}
	}
}
