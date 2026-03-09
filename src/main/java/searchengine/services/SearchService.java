package searchengine.services;

import searchengine.dto.search.SearchResponse;

public interface SearchService {
    SearchResponse getSearchPage(String query, String site, int offset, int limit);
}
