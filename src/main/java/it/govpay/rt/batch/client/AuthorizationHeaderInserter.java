package it.govpay.rt.batch.client;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.transport.HeadersAwareSenderWebServiceConnection;
import org.springframework.ws.transport.WebServiceConnection;
import org.springframework.ws.transport.context.TransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;

public class AuthorizationHeaderInserter implements ClientInterceptor{
	
	Logger log = LoggerFactory.getLogger(AuthorizationHeaderInserter.class);
	
	private final String headerName;
	private final String subscriptionKey; 
	

	public AuthorizationHeaderInserter(String headerName, String subscriptionKey) {
		this.headerName = headerName;
		this.subscriptionKey = subscriptionKey;
	}

	@Override
	public boolean handleRequest(MessageContext messageContext) throws WebServiceClientException {
		return true;
	}

	@Override
	public boolean handleResponse(MessageContext messageContext) throws WebServiceClientException {
		log.debug("Adding authentication header to the soap request");
		TransportContext context = TransportContextHolder.getTransportContext();
		WebServiceConnection connection = context.getConnection();
		if ( connection instanceof HeadersAwareSenderWebServiceConnection ) {
			HeadersAwareSenderWebServiceConnection httpConnection = (HeadersAwareSenderWebServiceConnection) context.getConnection();
			try {
				httpConnection.addRequestHeader(headerName, subscriptionKey);
			} catch (IOException e) {
				throw new WebServiceIOException("Fail to insert principal header", e);
			}
		}
		return true;
	}

	@Override
	public boolean handleFault(MessageContext messageContext) throws WebServiceClientException {
		return true;
	}

	@Override
	public void afterCompletion(MessageContext messageContext, Exception ex) throws WebServiceClientException {
		// Nothing to do
	}

}
