
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import com.alibaba.fastjson.JSONObject;


/**
 * 保持同一session的HttpClient工具类
 */
public class HttpClient {
	/**
	 * 上下文
	 */
	private HttpClientContext context = HttpClientContext.create();
	/**
	 * cookieStore 用于保存与服务端通信状态
	 */
	private CookieStore cookieStore = new BasicCookieStore();;
	private RequestConfig requestConfig = null;
	// 唯一一个client
	private CloseableHttpClient httpClient = null;

	public HttpClient() {
		init();
	}

	private void init() {
		// 配置超时时间（连接服务端超时1秒，请求数据返回超时2秒）
		requestConfig = RequestConfig.custom().setConnectTimeout(120000).setSocketTimeout(1160000)
				.setConnectionRequestTimeout(1160000).build();
		// 设置默认跳转以及存储cookie
		httpClient = HttpClientBuilder.create().setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy())
				.setRedirectStrategy(new DefaultRedirectStrategy()).setDefaultRequestConfig(requestConfig)
				.setDefaultCookieStore(cookieStore).build();

	}

	/**
	 * http get
	 * 
	 * @param url
	 * @return response
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public CloseableHttpResponse get(String url) throws ClientProtocolException, IOException {
		HttpGet httpget = new HttpGet(url);
		CloseableHttpResponse response = httpClient.execute(httpget, context);
		return response;
	}

	public String getString(String url) throws ClientProtocolException, IOException, ParseException, CallException {
		HttpGet httpget = new HttpGet(url);
		CloseableHttpResponse response = httpClient.execute(httpget, context);
		return this.getResponseString(response);
	}

	/**
	 * http post
	 * 
	 * @param url
	 * @param parameters
	 *            form表单
	 * @return response
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public CloseableHttpResponse post(String url, String parameters) throws ClientProtocolException, IOException {
		HttpPost httpPost = new HttpPost(url);
		List<NameValuePair> nvps = toNameValuePairList(parameters);
		httpPost.setEntity(new UrlEncodedFormEntity(nvps, "UTF-8"));
		CloseableHttpResponse response = httpClient.execute(httpPost, context);
		return response;

	}

	/**
	 * 表单类型俩种支持方式
	 * 
	 * @author Administrator
	 *
	 */
	public static enum PostTypeEnum {
		JSON, FORM
	}

	public String postString(String url) throws ClientProtocolException, IOException, ParseException, CallException {
		return getResponseString(post(url, PostTypeEnum.FORM, new HashMap<>()));
	}

	public String postString(String url, PostTypeEnum pte, Map<String, Object> parameters)
			throws ClientProtocolException, IOException, ParseException, CallException {

		return getResponseString(post(url, pte, parameters));
	}

	private CloseableHttpResponse post(String url, PostTypeEnum pte, Map<String, Object> parameters)
			throws ClientProtocolException, IOException {
		HttpPost httpPost = new HttpPost(url);

		if (pte.equals(PostTypeEnum.JSON)) {
			JSONObject jo = new JSONObject();
			if (parameters != null && parameters.size() > 0) {
				for (String key : parameters.keySet()) {
					jo.put(key, parameters.get(key));
				}
			}
			StringEntity entity = new StringEntity(jo.toJSONString(), "utf-8");
			entity.setContentEncoding("UTF-8");
			httpPost.setEntity(entity);
		} else {
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			if (parameters != null) {
				for (Entry<String, Object> ent : parameters.entrySet()) {
					nvps.add(new BasicNameValuePair(ent.getKey(), ent.getValue().toString()));
				}
			}
			httpPost.setEntity(new UrlEncodedFormEntity(nvps, "UTF-8"));
		}
		CloseableHttpResponse response = httpClient.execute(httpPost, context);
		return response;
	}

	@SuppressWarnings("unused")
	private List<NameValuePair> toNameValuePairList(String parameters) {
		List<NameValuePair> nvps = new ArrayList<NameValuePair>();
		if (parameters.isEmpty()) {
			return nvps;
		}
		String[] paramList = parameters.split("&");
		for (String parm : paramList) {
			int index = -1;
			for (int i = 0; i < parm.length(); i++) {
				index = parm.indexOf("=");
				break;
			}
			String key = parm.substring(0, index);
			String value = parm.substring(++index, parm.length());
			nvps.add(new BasicNameValuePair(key, value));
		}
		return nvps;
	}

	/**
	 * 手动增加cookie
	 * 
	 * @param name
	 * @param value
	 * @param domain
	 * @param path
	 */
	public void addCookie(String name, String value, String domain, String path) {
		BasicClientCookie cookie = new BasicClientCookie(name, value);
		cookie.setDomain(domain);
		cookie.setPath(path);
		cookieStore.addCookie(cookie);
	}

	/**
	 * 把结果console出来
	 * 
	 * @param httpResponse
	 * @throws ParseException
	 * @throws IOException
	 * @throws CallException
	 */
	public String getResponseString(HttpResponse httpResponse) throws ParseException, IOException, CallException {
		// 获取响应消息实体
		HttpEntity entity = httpResponse.getEntity();
		int s = httpResponse.getStatusLine().getStatusCode();
		if (s != 200) {
			throw new CallException(httpResponse);
		}
		if (entity != null) {
			try (InputStream stream = entity.getContent()) {
				String content = convertStreamToString(stream);
				return content;
			}
		}
		return "";
	}

	/**
	 * 失败回调参数
	 * 
	 * @author Administrator
	 *
	 */
	public static class CallException extends Exception {
		private HttpResponse httpResponse;
		private int code;

		CallException(HttpResponse httpResponse) {
			super("call err," + httpResponse.getStatusLine().getStatusCode());
			this.httpResponse = httpResponse;
		}

		public String getContent() {
			if (httpResponse == null) {
				return null;
			}
			HttpEntity entity = httpResponse.getEntity();
			if (entity == null) {
				return null;
			}
			try (InputStream stream = entity.getContent()) {
				String content = convertStreamToString(stream);
				return content;
			} catch (UnsupportedOperationException | IOException e) {
				Logger.error(this.getClass(), e);
			}
			return null;
		}

		public int getCode() {
			return code;
		}

	}

	/**
	 * 检查cookie的键值是否包含传参
	 * 
	 * @param key
	 * @return
	 */
	public boolean checkCookie(String key) {
		cookieStore = context.getCookieStore();
		List<Cookie> cookies = cookieStore.getCookies();
		boolean res = false;
		for (Cookie cookie : cookies) {
			if (cookie.getName().equals(key)) {
				res = true;
				break;
			}
		}
		return res;
	}

	/**
	 * 直接把Response内的Entity内容转换成String
	 * 
	 * @param httpResponse
	 * @return
	 * @throws ParseException
	 * @throws IOException
	 */
	public String toString(CloseableHttpResponse httpResponse) throws ParseException, IOException {
		// 获取响应消息实体
		HttpEntity entity = httpResponse.getEntity();
		if (entity != null)
			return EntityUtils.toString(entity);
		else
			return null;
	}

	public static String convertStreamToString(InputStream is) {
		/*
		 * To convert the InputStream to String we use the
		 * BufferedReader.readLine() method. We iterate until the BufferedReader
		 * return null which means there's no more data to read. Each line will
		 * appended to a StringBuilder and returned as String.
		 */
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		StringBuilder sb = new StringBuilder();

		String line = null;
		try {
			while ((line = reader.readLine()) != null) {
				sb.append(line + System.lineSeparator());
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return sb.toString();
	}

}
