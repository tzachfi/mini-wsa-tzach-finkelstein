package com.akamai.miniwsa.config;

import com.akamai.miniwsa.dto.TimeSeriesInterval;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class TimeSeriesIntervalConverter implements Converter<String, TimeSeriesInterval> {

    @Override
    public TimeSeriesInterval convert(String source) {
        return TimeSeriesInterval.fromToken(source);
    }
}
