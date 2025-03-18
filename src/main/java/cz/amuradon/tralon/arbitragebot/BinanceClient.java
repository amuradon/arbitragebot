package cz.amuradon.tralon.arbitragebot;

import java.util.List;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@RegisterRestClient(configKey = "mexc")
public interface BinanceClient {

	@Path("/ticker/price")
	@GET
	List<BinanceTickerDetail> ticker();

}
