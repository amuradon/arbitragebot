package cz.amuradon.tralon.arbitragebot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ScannerAgent {
	
	private final ScheduledExecutorService scheduler;
	
	private final MexcClient mexcClient;

	private final BinanceClient binanceClient;
	
	
	@Inject
    public ScannerAgent(
    		@RestClient final MexcClient mexcClient,
    		@RestClient final BinanceClient binanceClient
    		) {
    	
		scheduler = Executors.newScheduledThreadPool(2);
		this.mexcClient = mexcClient;
		this.binanceClient = binanceClient;
    }
    
	@Startup
    public void run() {
		Log.info("Scheduling...");
		ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(this::compare, 0, 20, TimeUnit.SECONDS);
		
		try {
			task.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new IllegalStateException("Something went wrong.", e);
		}
	}

	private void compare() {
		Log.info("Comparing...");
		Map<String, BigDecimal> mexcData = mexcClient.ticker().stream().collect(Collectors.toMap(e -> e.symbol(), e -> e.price()));
		Map<String, BigDecimal> binanceData = binanceClient.ticker().stream().collect(Collectors.toMap(e -> e.symbol(), e -> e.price()));
		
		for (Entry<String, BigDecimal> mexcEntry : mexcData.entrySet()) {
			String symbol = mexcEntry.getKey();
			BigDecimal binancePrice = binanceData.get(symbol);
			BigDecimal mexcPrice = mexcEntry.getValue();
			if (binancePrice != null) {
				Log.debugf("Symbol '%s' - MEXC %s, Binance %s", symbol, mexcPrice, binancePrice);
				if (binancePrice.compareTo(BigDecimal.ZERO) == 0 || mexcPrice.compareTo(BigDecimal.ZERO) == 0) {
					Log.debugf("Symbol '%s' has no price - MEXC %s, Binance %s", symbol, mexcPrice, binancePrice);
				} else {
					BigDecimal diff = mexcPrice.subtract(binancePrice)
							.divide(mexcPrice, 4, RoundingMode.HALF_UP).multiply(new BigDecimal(100));
					Log.debugf("Symbol '%s' price diff %s", symbol, diff);
					if (diff.abs().compareTo(new BigDecimal("0.5")) > 0) {
						Log.infof("Found opportunity '%s': %s", symbol, diff);
					}
				}
			}
		}
	}
}
