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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SampleSearch {
	public static void main(String[] args) throws IOException {
		List<Airport> airports = AirportParser.parseAirports(Path.of("airports.csv"));
		System.out.println("Read " + airports.size() + " airports.");
		buildIndex(airports);
		System.out.println("Built index.");
		System.out.println("Entering search-cli mode. Type a query.");
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

	public static void buildIndex(List<Airport> airports) throws IOException {
		Path indexDir = Path.of("airports-index");
		deleteDirRecursive(indexDir);
		Files.createDirectories(indexDir);

		try (
			Analyzer analyzer = new StandardAnalyzer();
			Directory luceneDir = FSDirectory.open(indexDir);
			IndexWriter indexWriter = new IndexWriter(luceneDir, new IndexWriterConfig(analyzer))
		) {
			for (var airport : airports) {
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
				indexWriter.addDocument(doc);
			}
		}
	}

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

	/**
	 * Helper function that removes a directory and its contents recursively.
	 * @param dir The directory to remove.
	 * @throws IOException If an error occurs.
	 */
	private static void deleteDirRecursive(Path dir) throws IOException {
		if (Files.notExists(dir)) return;
		Files.walkFileTree(dir, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
