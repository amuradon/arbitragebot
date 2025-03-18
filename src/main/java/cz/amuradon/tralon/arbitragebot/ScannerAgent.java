package cz.amuradon.tralon.arbitragebot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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
		scheduler.scheduleAtFixedRate(this::compare, 0, 20, TimeUnit.SECONDS);
	}

	private void compare() {
		Map<String, BigDecimal> mexcData = mexcClient.ticker().stream().collect(Collectors.toMap(e -> e.symbol(), e -> e.price()));
		Map<String, BigDecimal> binanceData = binanceClient.ticker().stream().collect(Collectors.toMap(e -> e.symbol(), e -> e.price()));
		
		for (Entry<String, BigDecimal> mexcEntry : mexcData.entrySet()) {
			BigDecimal binancePrice = binanceData.get(mexcEntry.getKey());
			BigDecimal diff = mexcEntry.getValue().subtract(binancePrice)
					.divide(mexcEntry.getValue(), 4, RoundingMode.HALF_UP).multiply(new BigDecimal(100));
			if (diff.abs().compareTo(BigDecimal.ONE) >= 1) {
				Log.infof("Found opportunity '%s': %d", mexcEntry.getKey(), diff);
			}
		}
	}
}
