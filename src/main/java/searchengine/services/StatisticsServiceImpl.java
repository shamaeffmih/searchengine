package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.models.SiteEntity;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    @Autowired
    private final SiteRepository siteRepository;

    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private final LemmaRepository lemmaRepository;
    private final SitesList sites;

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<SiteEntity> sitesList = siteRepository.findAll();
        for (SiteEntity site : sitesList) {
            DetailedStatisticsItem item = setSiteStatistic(site);
            detailed.add(item);
        }
        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        total.setLemmas((int) lemmaRepository.count());
        total.setPages((int) pageRepository.count());
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
    DetailedStatisticsItem setSiteStatistic(SiteEntity siteEntity) {
        DetailedStatisticsItem item = new DetailedStatisticsItem();
        item.setName(siteEntity.getName());
        item.setUrl(siteEntity.getUrl());
        item.setPages(getPagesCount(siteEntity));
        item.setLemmas(getLemmasCount(siteEntity));
        item.setStatusTime(localDataTimeToMillis(siteEntity.getStatusTime()));
        item.setStatus(siteEntity.getStatus().toString());
        item.setError(siteEntity.getLastError());
        return item;
    }
    private int getPagesCount(SiteEntity siteId){
        return pageRepository.countPagesBySiteId(siteId);

    }

    private int getLemmasCount(SiteEntity siteId){
        return lemmaRepository.countLemmasBySiteId(siteId);

    }
    private Long localDataTimeToMillis(LocalDateTime ldt) {
        ZonedDateTime zdt = ZonedDateTime.of(ldt, ZoneId.systemDefault());
        return zdt.toInstant().toEpochMilli();
    }
}
