package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.models.IndexEntity;
import searchengine.models.LemmaEntity;
import searchengine.models.PageEntity;
import searchengine.models.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.utils.LemmaFinder;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private final SiteRepository siteRepository;
    @Autowired
    private final LemmaRepository lemmaRepository;
    @Autowired
    private final IndexRepository indexRepository;
    private SiteEntity siteEntity;
    private LemmaEntity lemmaEntity;
    private IndexEntity indexEntity;
    protected LemmaFinder lemmaFinder;

    {
        try {
            lemmaFinder = LemmaFinder.getInstance();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Autowired
    private final SitesList sites = new SitesList();
    protected static final int TOO_MANY_PAGES = 1000;
    private static final int LENGTH_PHRASE = 230;
    List<SearchData> totalData;
    Map<Integer, Integer> mapPositionAndLengthWord = new TreeMap<>();

    @Override
    @Transactional
    public SearchResponse getSearchPage(String query, String site, int offset, int limit) {
        SearchResponse response = new SearchResponse();
        totalData = new ArrayList<>();
        if (query.equals("")) {
            response.setResult(false);
            response.setError("Задан пустой поисковый запрос");
            return response;
        }
        Set<String> set = lemmaFinder.getLemmaSet(query);
        if (set.size() == 0) {
            response.setResult(false);
            response.setError("Пожалуйста, введите поисковый запрос на киррилице");
            return response;
        }
        if (site == null) {
            getPagesFromAllSites(query, offset, limit);
            response.setCount(getAllPagesByRelevance(query).size());
        } else {
            getPagesFromOneSites(query, site, offset, limit);
            response.setCount(getPagesByRelevance(site, query).size());
        }
        setResponseNotError(response);
        return response;
    }

    private void setResponseNotError(SearchResponse response) {
        response.setData(totalData);
        response.setError("");
        response.setResult(true);
    }

    private void getPagesFromAllSites(String query, int offset, int limit) {
        showAllPagesOnFront(query, offset, limit)
                .forEach(p -> {
                    try {
                        totalData.add(getPageData(query, p, null));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private void getPagesFromOneSites(String query, String site, int offset, int limit) {
        showPagesOnFront(site, query, offset, limit)
                .forEach(p -> {
                    try {
                        totalData.add(getPageData(query, p, site));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private SearchData getPageData(String query, PageEntity p, String site) throws IOException {
        SearchData pageData = new SearchData();
        String siteForData;
        String siteWithoutSlash;
        if (site == null) {
            siteWithoutSlash = p.getSiteId().getUrl().substring(0, p.getSiteId().getUrl().length() - 1);
            siteForData = p.getSiteId().getUrl();
        } else {
            siteWithoutSlash = site.substring(0, site.length() - 1);
            siteForData = site;
        }
        pageData.setRelevance(getRelevancePage(p, siteForData, query));
        pageData.setSite(siteWithoutSlash);
        pageData.setSiteName(getSiteName(siteWithoutSlash));
        pageData.setSnippet(snippet(p, siteForData, query));
        pageData.setTitle(getTitlePage(p));
        pageData.setUri(p.getPath());
        return pageData;
    }

    private List<PageEntity> showPagesOnFront(String site, String query, int offset, int limit) {
        List<PageEntity> result;
        List<PageEntity> pageEntityList = getPagesByRelevance(site, query);
        if (pageEntityList.size() > offset + limit) {
            result = pageEntityList.subList(offset, offset + limit);
        } else {
            result = pageEntityList.subList(offset, pageEntityList.size());
        }
        return result;
    }

    private List<PageEntity> showAllPagesOnFront(String query, int offset, int limit) {
        List<PageEntity> result;
        List<PageEntity> pageEntityList = getAllPagesByRelevance(query);
        if (pageEntityList.size() > offset + limit) {
            result = pageEntityList.subList(offset, offset + limit);
        } else {
            result = pageEntityList.subList(offset, pageEntityList.size());
        }
        return result;
    }

    private String getSiteName(String siteUrl) {
        Document doc;
        try {
            doc = Jsoup.connect(siteUrl).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return Objects.requireNonNull(doc.select("head > title").first()).text();
    }

    private String getTitlePage(PageEntity pageEntity) {
        return Jsoup.parse(pageEntity.getContent()).title();
    }

    private int boyerMooreSimpleSearch(String cleanTextHtml, String query) {
        char[] text = cleanTextHtml.toLowerCase().toCharArray();
        char[] pattern = query.toLowerCase().toCharArray();
        int textSize = text.length;
        int patternSize = pattern.length;

        int i = 0, j;
        while ((i + patternSize) <= textSize) {
            j = patternSize - 1;
            while (text[i + j] == pattern[j]) {
                j--;
                if (j < 0)
                    return i;
            }
            i++;
        }
        return -1;
    }

    private String searchPhraseWordsAppearSequentially(String cleanTextHtml, String query) {
        String cutCut;
        int begin = boyerMooreSimpleSearch(cleanTextHtml, query);
        if (begin == -1) {
            return "";
        }
        int end = begin + LENGTH_PHRASE;
        if (cleanTextHtml.length() < end) {
            begin = cleanTextHtml.length() - LENGTH_PHRASE;
            end = cleanTextHtml.length();
        }
        cutCut = cleanTextHtml.substring(begin, end);
        return cutCut;
    }

    private List<Integer> sortPositionOfWords(String cleanText, List<String> listQuery) {
        String[] words = cleanText.split(" ");
        Map<String, List<Integer>> map = new HashMap<>();
        listQuery.forEach(s -> {
            int position = 0;
            List<Integer> list = new ArrayList<>();
            setThroughAllWordsOfText(words, s, cleanText, position, list);
            map.put(s, list);
        });
        return map.entrySet().stream()
                .flatMap(x -> x.getValue().stream())
                .sorted()
                .toList();
    }

    private void setThroughAllWordsOfText(String[] words, String s, String cleanText, int position, List<Integer> list) {
        for (String wordsI : words) {
            String word = lemmaFinder.convertWordToLemma(wordsI);
            if (s.equals(word)) {
                String cleanTextHtmlIndexOf = cleanText.substring(position);
                int pos = cleanTextHtmlIndexOf.indexOf(" " + wordsI) + 1;
                position = position + pos;
                list.add(position);
                mapPositionAndLengthWord.put(position, wordsI.length());
            }
        }
    }

    private Map<Integer, List<Integer>> mappingPositionsAndDistances(List<Integer> sortingListPositionOfWords, List<String> listQuery) {
        Map<Integer, List<Integer>> mapPosAndDiff = new TreeMap<>();
        List<Integer> listDistances;
        for (int i = 0; i < sortingListPositionOfWords.size(); i++) {
            listDistances = new ArrayList<>();
            int current = sortingListPositionOfWords.get(i);
            for (Integer listPositionOfWord : sortingListPositionOfWords) {
                int next = listPositionOfWord;
                int distance = Math.abs(current - next);
                listDistances.add(distance);
            }
            List<Integer> listLimit = listDistances
                    .stream()
                    .filter(el -> el != 0)
                    .sorted()
                    .limit(listQuery.size() - 1)
                    .toList();
            mapPosAndDiff.put(current, listLimit);
        }
        return mapPosAndDiff;
    }

    private Set<Integer> getAllListFromMapDistanceLimit(Map<Integer, List<Integer>> mapPosAndDist, List<String> listQuery) {
        List<Integer> allList = new ArrayList<>();
        mapPosAndDist.forEach((key, value) -> allList.addAll(value));
        return new TreeSet<>(allList).stream()
                .limit(listQuery.size())
                .collect(Collectors.toSet());
    }

    private List<Integer> getBestPositions(int maxDistance, Map<List<Integer>, Integer> mapCommonElements) {
        List<Integer> listBest = new ArrayList<>();
        if (maxDistance < 1) {
            return listBest;
        }
        for (Map.Entry<List<Integer>, Integer> entry : mapCommonElements.entrySet()) {
            if (entry.getValue() == maxDistance) {
                listBest = entry.getKey();
            }
        }
        return listBest;
    }

    private List<List<Integer>> getBestDistanceQueryWords(List<List<Integer>> listListAllDistance,
                                                          int maxDistance,
                                                          List<Integer> listBest,
                                                          List<List<Integer>> commonElements,
                                                          Set<Integer> setOfListsFromMapDistLimit) {
        List<List<Integer>> listContainBestDistQueryWords = new ArrayList<>();
        for (List<Integer> integers : listListAllDistance) {
            List<Integer> list1 = new ArrayList<>(integers);
            if (!list1.retainAll(setOfListsFromMapDistLimit)) {
                listContainBestDistQueryWords.add(list1);
            }
        }
        if (listContainBestDistQueryWords.isEmpty()) {
            if (maxDistance > 1) {
                listContainBestDistQueryWords.add(listBest);
            } else {
                listContainBestDistQueryWords.add(commonElements.get(0));
            }
        }
        return listContainBestDistQueryWords;
    }

    private List<Integer> getPositionWordsInTextFromQuery(List<List<Integer>> listContainBestDistQueryWords,
                                                          Map<Integer, List<Integer>> mapPosAndDiff) {
        return listContainBestDistQueryWords.stream()
                .flatMap(innerList -> mapPosAndDiff.entrySet().stream()
                        .filter(entry -> entry.getValue().equals(innerList))
                        .map(Map.Entry::getKey))
                .toList();
    }

    public String getSnippetPhrase(String cleanTextHtml, String query) {
        String cleanText = cleanTextHtml.trim();
        List<String> listQuery = getUniqueLemmas(query);
        Map<Integer, List<Integer>> mapPosAndDist = mappingPositionsAndDistances(sortPositionOfWords(cleanText, listQuery), listQuery);
        Set<Integer> setOfListsFromMapDistLimit = getAllListFromMapDistanceLimit(mapPosAndDist, listQuery);
        List<List<Integer>> listListAllDistance = mapPosAndDist.values().stream().toList();
        List<List<Integer>> commonElements = new ArrayList<>();
        Map<List<Integer>, Integer> mapCommonElements = new HashMap<>();
        listListAllDistance.forEach(list -> {
            int counterCommonElements = 0;
            for (Integer element : list) {
                if (setOfListsFromMapDistLimit.contains(element)) {
                    counterCommonElements++;
                    commonElements.add(list);
                    mapCommonElements.put(list, counterCommonElements);
                }
            }
        });
        int maxDifferences = mapCommonElements.values().stream()
                .max(Integer::compareTo)
                .orElse(0);
        List<Integer> listBest = getBestPositions(maxDifferences, mapCommonElements);
        List<List<Integer>> listContainBestDiffQueryWords = getBestDistanceQueryWords(listListAllDistance, maxDifferences, listBest, commonElements, setOfListsFromMapDistLimit);
        List<Integer> positionWords = getPositionWordsInTextFromQuery(listContainBestDiffQueryWords, mapPosAndDist);
        int[] bounds = calculateSnippetBounds(cleanText, positionWords);
        return cleanText.substring(bounds[0], bounds[1]);
//        return cleanText.substring(beginIndex, endIndex);
    }


    private int[] calculateSnippetBounds(String cleanText, List<Integer> positionWords) {
        int startClean = positionWords.get(0);
        int endClean = positionWords.get(positionWords.size() - 1);
        int startResForSnippet = cleanText.indexOf(
                cleanText.substring(startClean, endClean + mapPositionAndLengthWord.get(endClean)));
        int beginIndex = Math.max(0, startResForSnippet - LENGTH_PHRASE / 2);
        int endIndex = Math.min(cleanText.length(), startResForSnippet + LENGTH_PHRASE / 2);
        return new int[]{beginIndex, endIndex};
    }

    private String snippet(PageEntity pageEntity, String site, String query) {
        String textForSnippetWithoutN = lemmaFinder.cleanUpCodePage(pageEntity);
        String result = searchPhraseWordsAppearSequentially(textForSnippetWithoutN, query);
        if (result.equals("")) {
            result = getSnippetPhrase(textForSnippetWithoutN, query);
        }
        String regex = " ";
        List<String> lemma = getWithoutLemmasMeetsOnTooManyPages(site, query);
        String[] cutWords = result.split(regex);
        for (String l : lemma) {
            for (int i = 0; i < cutWords.length; i++) {
                Set<String> cutWordConvertLemma = lemmaFinder.getLemmaSet(cutWords[i]);
                cutWords[i] = cutWordConvertLemma.contains(l) ? ("<b>" + cutWords[i] + "</b>") : cutWords[i];
            }
        }
        result = String.join(" ", cutWords);
        log.info(result);
        return String.join("\n", "... " + result) + " ...";
    }

    private List<String> getUniqueLemmas(String query) {
        return lemmaFinder.getLemmaList(query);
    }

    private List<String> getWithoutLemmasMeetsOnTooManyPages(String siteName, String query) {
        List<String> resultList = getUniqueLemmas(query);
        Set<String> itemsForRemove = new HashSet<>();
        for (String lemma : resultList) {
            if (defineLimitFrequencyLemmas(siteName, lemma) > TOO_MANY_PAGES) {
                itemsForRemove.add(lemma);
            }
        }
        resultList.removeAll(itemsForRemove);
        return resultList;
    }

    private int defineLimitFrequencyLemmas(String siteName, String lemma) {
        siteEntity = siteRepository.findByUrl(siteName);
        SiteEntity siteId = siteRepository.getReferenceById(siteEntity.getId());
        lemmaEntity = lemmaRepository.findByLemmaAndSiteId(lemma, siteId);
        int frequencyLemma = lemmaEntity == null ? 0 : lemmaEntity.getFrequency();

        if (frequencyLemma > TOO_MANY_PAGES) {
            System.out.println("Лемма " + lemmaEntity.getLemma() + " встречает на слишком большом кол-ве страниц"
                    + " сайта - " + siteName);
        }
        return frequencyLemma;
    }

    private Map<String, Integer> sortMapLemmas(String siteName, String query) {
        HashMap<String, Integer> map = new HashMap<>();
        List<String> listWithoutLemmas = getWithoutLemmasMeetsOnTooManyPages(siteName, query);
        listWithoutLemmas.forEach(l -> map.put(l, defineLimitFrequencyLemmas(siteName, l)));
        return sortMapByValue(map);
    }

    private String getFirstRarestLemma(Map<String, Integer> map) {
        String rarestLemma = null;
        Iterator<Map.Entry<String, Integer>> entries = map.entrySet().iterator();
        if (entries.hasNext()) {
            Map.Entry<String, Integer> firstEntry = entries.next();
            rarestLemma = firstEntry.getKey();
        }
        return rarestLemma;
    }

    private List<PageEntity> getPagesByRarestLemma(String siteName, String query) {
        Map<String, Integer> sortingMapLemmas = sortMapLemmas(siteName, query);
        List<PageEntity> pageEntityList = new ArrayList<>();
        List<Integer> pageIdList = new ArrayList<>();
        String firstLemma = getFirstRarestLemma(sortingMapLemmas);

        siteEntity = siteRepository.findByUrl(siteName);
        SiteEntity siteId = siteRepository.getReferenceById(siteEntity.getId());
        lemmaEntity = lemmaRepository.findByLemmaAndSiteId(firstLemma, siteId);
        if (lemmaEntity != null) {
            List<IndexEntity> indexEntityList = indexRepository.findAllByLemmaId(lemmaEntity);
            indexEntityList.forEach(i -> {
                int pageInt = i.getPageId().getId();
                pageIdList.add(pageInt);
            });
        } else {
            return pageEntityList;
        }
        pageEntityList = pageRepository.findAllById(pageIdList);
        return pageEntityList;
    }

    private List<PageEntity> getPagesToDisplayOnRarestLemma(String siteName, String query) {
        Map<String, Integer> sortingMapLemmas = sortMapLemmas(siteName, query);
        List<PageEntity> pageEntityListResult = getPagesByRarestLemma(siteName, query);
        SiteEntity siteEntityId = siteRepository.findByUrl(siteName);
        List<PageEntity> pageEntityListCurrent = new ArrayList<>();

        pageEntityListResult.forEach(pageEntity -> {
            for (Map.Entry<String, Integer> entry : sortingMapLemmas.entrySet()) {
                String lemma = entry.getKey();
                LemmaEntity lemmaEntity = lemmaRepository.findByLemmaAndSiteId(lemma, siteEntityId);
                boolean existLemmaPage = indexRepository.existsByLemmaIdAndPageId(lemmaEntity, pageEntity);
                if (existLemmaPage) {
                    pageEntityListCurrent.add(pageEntity);
                }
            }
        });
        pageEntityListResult.retainAll(pageEntityListCurrent);
        return pageEntityListResult;
    }

    private Map<PageEntity, Float> getAbsoluteRelevancePages(String siteName, String query) {
        Map<PageEntity, Float> result = new HashMap<>();
        List<PageEntity> pagesForReturn = getPagesToDisplayOnRarestLemma(siteName, query);
        List<String> lemmas = getWithoutLemmasMeetsOnTooManyPages(siteName, query);
        pagesForReturn.forEach(pageEntity -> {
            float absRelevanceOfPage = 0F;
            for (String lemma : lemmas) {
                lemmaEntity = lemmaRepository.findByLemmaAndSiteId(lemma, siteEntity);
                if (!indexRepository.existsByLemmaIdAndPageId(lemmaEntity, pageEntity)) {
                    pageEntity = null;
                    continue;
                }
                indexEntity = indexRepository.findByLemmaIdAndPageId(lemmaEntity, pageEntity);
                float rank = indexEntity.getRank();
                absRelevanceOfPage += rank;
            }
            if (pageEntity != null) {
                result.put(pageEntity, absRelevanceOfPage);
            }
        });
        return result;
    }

    private Map<PageEntity, Float> getRelativeRelevancePages(String siteName, String query) { //относительная релевантность
        float maxAbsRelevance;
        Map<PageEntity, Float> map = getAbsoluteRelevancePages(siteName, query);
        Map<PageEntity, Float> result = new HashMap<>();
        if (map.size() != 0) {
            Map.Entry<PageEntity, Float> maxEntry = Collections.max(map.entrySet(), Map.Entry.comparingByValue());
            maxAbsRelevance = maxEntry.getValue();
            for (Map.Entry<PageEntity, Float> entry : map.entrySet()) {
                result.put(entry.getKey(), entry.getValue() / maxAbsRelevance);
            }
        }
        return sortPagesByRelevance(result);
    }

    private List<PageEntity> getPagesByRelevance(String site, String query) {
        Map<PageEntity, Float> resultRelevance = getRelativeRelevancePages(site, query);
        List<PageEntity> resultIssue = new ArrayList<>();
        Iterator<Map.Entry<PageEntity, Float>> entries = resultRelevance.entrySet().iterator();
        PageEntity key;
        while (entries.hasNext()) {
            Map.Entry<PageEntity, Float> entry = entries.next();
            key = entry.getKey();
            resultIssue.add(key);
        }
        return resultIssue;
    }

    public static <K, V extends Comparable<? super V>> Map<K, V> sortMapByValue(Map<K, V> map) {
        Map<K, V> result = new LinkedHashMap<>();
        Stream<Map.Entry<K, V>> st = map.entrySet().stream();
        st.sorted(Map.Entry.comparingByValue()).forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    private Map<PageEntity, Float> sortPagesByRelevance(Map<PageEntity, Float> map) {
        return sortMapByValue(map).entrySet()
                .stream()
                .sorted(Map.Entry.<PageEntity, Float>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (oldVal, newVal) -> oldVal, LinkedHashMap::new
                ));
    }

    private List<PageEntity> getAllPagesByRelevance(String query) {
        List<Site> allSitesSelected = sites.getSites();
        Map<PageEntity, Float> resultRelevance = new HashMap<>();
        Map<PageEntity, Float> mapRelevance;
        for (Site s : allSitesSelected) {
            mapRelevance = getRelativeRelevancePages(s.getUrl(), query);
            resultRelevance.putAll(mapRelevance);
        }
        List<PageEntity> result = new ArrayList<>();
        Iterator<Map.Entry<PageEntity, Float>> entries = sortPagesByRelevance(resultRelevance).entrySet().iterator();
        PageEntity key;
        while (entries.hasNext()) {
            Map.Entry<PageEntity, Float> entry = entries.next();
            key = entry.getKey();
            result.add(key);
        }
        return result;
    }

    private Float getRelevancePage(PageEntity pageEntity, String site, String query) {
        Float result = 0F;
        Map<PageEntity, Float> relevancePage = getRelativeRelevancePages(site, query);
        for (Map.Entry<PageEntity, Float> entry : relevancePage.entrySet()) {
            if (entry.getKey() == pageEntity) {
                result = entry.getValue();
                break;
            }
        }
        return result;
    }
}
