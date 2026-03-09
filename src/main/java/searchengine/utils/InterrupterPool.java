package searchengine.utils;

import lombok.Data;

@Data
public class InterrupterPool {
    private volatile boolean stop = true;
}
