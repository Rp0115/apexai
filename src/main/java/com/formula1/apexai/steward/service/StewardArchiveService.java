package com.formula1.apexai.steward.service;

import com.formula1.apexai.steward.model.StewardDocument;
import com.formula1.apexai.steward.repository.StewardDocumentRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StewardArchiveService {

	private final StewardDocumentRepository repository;
	private final ObjectProvider<VectorStore> vectorStoreProvider;

	@PostConstruct
	@Transactional
	public void seedJapan2023Narratives() {
		if (repository.count() > 0) {
			return;
		}

		List<StewardDocument> seeds = List.of(
				doc("2023 Japanese Grand Prix – Race Overview",
						"""
								The 2023 Formula 1 Japanese Grand Prix was held at Suzuka Circuit on 24 September 2023.
								Max Verstappen (Red Bull Racing) won the race and mathematically secured his third World Drivers' Championship.
								The race distance was 53 laps. Soft, medium, and hard Pirelli compounds were available.
								Weather was dry. Red Bull's dominant pace on the medium tyre in the middle stint proved decisive.
								"""),
				doc("Steward Note – Track Limits & Racing Incidents (Japan 2023)",
						"""
								FIA Stewards reviewed several track-limits infringements at Suzuka Turns 16/17 during the 2023 Japanese Grand Prix weekend.
								Drivers repeatedly exceeding track limits in qualifying had lap times deleted per the event notes.
								No race-stopping red flag was required. The stewards emphasized consistency with prior Suzuka event notes regarding the exit of the final chicane.
								Minor on-track contacts were noted but did not result in time penalties that changed the podium order.
								"""),
				doc("Championship Narrative – Verstappen Clinches Title at Suzuka",
						"""
								By winning the 2023 Japanese Grand Prix, Max Verstappen clinched the Drivers' Championship with races remaining.
								Sergio Perez finished on the podium for Red Bull Racing. Carlos Sainz and the Ferrari team fought for remaining points.
								McLaren showed competitive race pace with Lando Norris. The Suzuka result underscored Red Bull's season-long advantage in race tyre management.
								Historical context: Suzuka has previously hosted title-deciding races, including legendary battles in the late 1980s and 1990s.
								"""),
				doc("FIA Document Summary – Parc Fermé and Technical Compliance (Japan 2023)",
						"""
								Cars entered parc fermé conditions after qualifying at the 2023 Japanese Grand Prix.
								Technical delegates reported no major irregularities affecting classification.
								Teams followed the event's fuel and tyre allocation rules. Power unit change penalties, if any, were published in the stewards' documents prior to the race start.
								This archive entry is a narrative summary for RAG demonstration and is not an official FIA PDF transcript.
								""")
		);

		repository.saveAll(seeds);
		indexIntoVectorStore(seeds);
		log.info("Seeded {} steward archive documents for Japan 2023", seeds.size());
	}

	@Transactional
	public StewardDocument ingest(String title, String content, String source) {
		StewardDocument document = new StewardDocument();
		document.setTitle(title);
		document.setContent(content);
		document.setSource(source == null ? "upload" : source);
		document.setEventName("Japanese Grand Prix");
		document.setYear(2023);
		StewardDocument saved = repository.save(document);
		indexIntoVectorStore(List.of(saved));
		return saved;
	}

	public String search(String query) {
		VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
		if (vectorStore != null) {
			try {
				List<Document> hits = vectorStore.similaritySearch(
						SearchRequest.builder().query(query).topK(3).similarityThreshold(0.4).build());
				if (hits != null && !hits.isEmpty()) {
					return hits.stream()
							.map(d -> "• " + d.getText())
							.collect(Collectors.joining("\n\n"));
				}
			} catch (Exception ex) {
				log.warn("Vector search unavailable, falling back to SQL text search: {}", ex.getMessage());
			}
		}

		List<StewardDocument> docs = repository.searchText(sanitize(query));
		if (docs.isEmpty()) {
			docs = repository.findAll().stream().limit(2).toList();
		}
		return docs.stream()
				.map(d -> "• [" + d.getTitle() + "] " + d.getContent().trim())
				.collect(Collectors.joining("\n\n"));
	}

	private void indexIntoVectorStore(List<StewardDocument> docs) {
		VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
		if (vectorStore == null) {
			return;
		}
		try {
			List<Document> aiDocs = new ArrayList<>();
			for (StewardDocument d : docs) {
				aiDocs.add(new Document(
						d.getTitle() + "\n\n" + d.getContent(),
						Map.of("title", d.getTitle(), "year", String.valueOf(d.getYear()), "source", d.getSource())));
			}
			vectorStore.add(aiDocs);
		} catch (Exception ex) {
			log.warn("Could not embed steward docs into pgvector (is GEMINI_API_KEY set?): {}", ex.getMessage());
		}
	}

	private StewardDocument doc(String title, String content) {
		StewardDocument d = new StewardDocument();
		d.setTitle(title);
		d.setContent(content);
		d.setSource("seed");
		d.setEventName("Japanese Grand Prix");
		d.setYear(2023);
		return d;
	}

	private String sanitize(String query) {
		if (query == null || query.isBlank()) {
			return "Suzuka";
		}
		return query.replace("%", "").replace("_", " ").trim();
	}
}
