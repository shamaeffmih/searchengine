package searchengine.dto.statistics;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StatisticsResponse {
    private boolean result;
    private StatisticsData statistics;
}
