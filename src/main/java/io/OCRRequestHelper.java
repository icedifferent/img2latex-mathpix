package io;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLContext;
import java.io.EOFException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.UnknownHostException;


/**
 * IO.OCRRequestHelper.java
 * handles the OCR request with HTTP post.
 * Parsing the result as a IO.Response object.
 */
public class OCRRequestHelper {

    /**
     * Send the request with Json parameters to Mathpix API.
     * Parsing the result as a IO.Response object.
     *
     * @param parameters JsonObject to send as the request parameters.
     * @return a IO.Response object.
     */
    public static Response getResult(JsonObject parameters) {

        String app_id;
        String app_key;

        APICredentialConfig APICredentialConfig = IOUtils.getAPICredentialConfig();

        if (APICredentialConfig.isValid()) {
            app_id = APICredentialConfig.getAppId();
            app_key = APICredentialConfig.getAppKey();
        } else {
            // early return
            return new Response(IOUtils.INVALID_CREDENTIALS_ERROR);
        }

        // workaround to resolve #26
        SSLContext context = SSLContexts.createSystemDefault();
        SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(context, IOUtils.SUPPORTED_PROTOCOLS, null, NoopHostnameVerifier.INSTANCE);

        RequestConfig requestConfig;

        if (IOUtils.getProxyEnabled()) {
            // proxy enabled
            ProxyConfig proxyConfig = IOUtils.getProxyConfig();
            if (proxyConfig.isValid()) {
                HttpHost proxy = new HttpHost(proxyConfig.getHostname(), proxyConfig.getPort());
                // maximum connection waiting time 10 seconds
                requestConfig = RequestConfig.custom().setConnectTimeout(10000).setProxy(proxy).build();
            } else {
                return new Response(IOUtils.INVALID_PROXY_CONFIG_ERROR);
            }
        } else {
            // maximum connection waiting time 10 seconds
            requestConfig = RequestConfig.custom().setConnectTimeout(10000).build();
        }

        // build the HTTP client with above config
        CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).setSSLSocketFactory(sslConnectionSocketFactory).build();

        // set the request parameters
        StringEntity requestParameters;
        try {
            requestParameters = new StringEntity(parameters.toString());
        } catch (UnsupportedEncodingException e) {
            return null;
        }

        // OCR API url
        HttpPost request = new HttpPost(IOUtils.OCR_API_URL);

        // with app_id, app_key, and json type content as the post header
        request.addHeader("app_id", app_id);
        request.addHeader("app_key", app_key);
        request.addHeader("Content-type", "application/json");
        request.setEntity(requestParameters);

        String json;
        try {
            // get the raw result from the execution
            HttpResponse result = httpClient.execute(request);
            // obtain the message entity of this response
            json = EntityUtils.toString(result.getEntity(), "UTF-8");
        } catch (UnknownHostException e) {
            return new Response(IOUtils.UNKNOWN_HOST_ERROR);
        } catch (ConnectException e) {
            return new Response(IOUtils.CONNECTION_REFUSED_ERROR);
        } catch (EOFException e) {
            return new Response(IOUtils.SSL_PEER_SHUT_DOWN_INCORRECTLY_ERROR);
        } catch (IOException e) {
            return null;
        }

        // return the parsed result
        return new Gson().fromJson(json, Response.class);

    }

}
