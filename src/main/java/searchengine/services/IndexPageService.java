package searchengine.services;

import searchengine.dto.index.IndexPageResponse;

public interface IndexPageService {
    IndexPageResponse getIndexPage(String url);
}
