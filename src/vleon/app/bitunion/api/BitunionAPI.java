package vleon.app.bitunion.api;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class BitunionAPI {
	public static final int NETERROR = -1;
	public static final int SESSIONERROR = 0;
	public static final int NONE = 1;
	public static final int UNKNOWNERROR = -2;
	public static final int OUTNET = 1;
	public static final int INNET = 0;
	// {"result":"fail","msg":"IP+logged"}
	String BASEURL = "http://www.bitunion.org/open_api/";
	String REQUEST_LOGGING, REQUEST_FORUM, REQUEST_THREAD, REQUEST_POST, REQUEST_PROFILE,
			NEWPOST, NEWTHREAD;

	public enum LoginResult {
		SUCCESS, SESSIONLOGIN, FAILURE, NETWRONG, UNKNOWN;
		// 根据ordinal值获得枚举类型
		public static LoginResult valueOf(int ordinal) {
			if (ordinal < 0 || ordinal >= values().length)
				return UNKNOWN;
			return values()[ordinal];
		}
	};

	String mUsername, mPassword;
	String mSession = null;
	boolean isLogined;
	HttpParams httpParams;
	DefaultHttpClient client;
	CookieStore cookieStore;
	private Cookie mCookie;
	int flagCnt = 0;
	private int mError = NONE;
	int mNetType;

	public BitunionAPI(String username, String password) {
		this.mUsername = username;
		this.mPassword = password;
		this.isLogined = false;
		httpParams = new BasicHttpParams();
		HttpConnectionParams.setConnectionTimeout(httpParams, 2000);
		HttpConnectionParams.setSoTimeout(httpParams, 3000);
		client = new DefaultHttpClient(httpParams);
		// switchToNet(mNetType);
	}

	public BitunionAPI(String username, String password, String session) {
		this.mUsername = username;
		this.mPassword = password;
		this.mSession = session;
	}

	public void switchToNet(int net) {
		mNetType = net;
		if (net == INNET) {
			BASEURL = "http://www.bitunion.org/open_api/";
		} else if (net == OUTNET) {
			BASEURL = "http://out.bitunion.org/open_api/";
		}
		REQUEST_LOGGING = BASEURL + "bu_logging.php";
		REQUEST_FORUM = BASEURL + "bu_forum.php";
		REQUEST_THREAD = BASEURL + "bu_thread.php";
		REQUEST_PROFILE = BASEURL + "bu_profile.php";
		REQUEST_POST = BASEURL + "bu_post.php";
		NEWPOST = BASEURL + "bu_newpost.php";
		NEWTHREAD = BASEURL + "bu_newpost.php";
	}

	public boolean available() {
		if (mSession == null)
			return false;
		return true;
	}

	public String getSession() {
		return this.mSession;
	}

	public LoginResult apiLogin() {
		JSONObject jsonObj = new JSONObject();
		try {
			jsonObj.put("action", "login");
			jsonObj.put("username", this.mUsername);
			jsonObj.put("password", this.mPassword);
		} catch (JSONException e1) {
			e1.printStackTrace();
		}
		JSONObject obj = post(REQUEST_LOGGING, jsonObj);
		if (obj == null) {
			isLogined = false;
			return LoginResult.NETWRONG;
		}
		String result = getString(obj, "result");
		if (result.equals("success")) {
			isLogined = true;
			this.mSession = getString(obj, "session");
			this.cookieStore = client.getCookieStore();
			return LoginResult.SUCCESS;
		} else {
			isLogined = false;
			return LoginResult.FAILURE;
		}
	}

	public String apiLogout() {
		JSONObject jsonObj = new JSONObject();
		try {
			jsonObj.put("action", "logout");
			jsonObj.put("username", this.mUsername);
			jsonObj.put("password", this.mPassword);
			jsonObj.put("session", this.mSession);
		} catch (JSONException e1) {
			e1.printStackTrace();
		}
		JSONObject obj = post(REQUEST_LOGGING, jsonObj);
		if (obj == null) {
			return "network failure";
		}
		String result = getString(obj, "result");
		if (result.equals("success")) {
			isLogined = false; // 注销成功
			mSession = null;
		}
		return result;
	}

	/*
	 * 得到论坛列表
	 */
	public JSONObject getForumList() {
		JSONObject jsonObj = new JSONObject();
		try {
			jsonObj.put("action", "forum");
			jsonObj.put("username", this.mUsername);
			jsonObj.put("session", this.mSession);
		} catch (JSONException e1) {
			e1.printStackTrace();
		}
		JSONObject obj = post(REQUEST_FORUM, jsonObj);
		return obj;
	}

	/*
	 * 查询用户信息 
	 */
	public BuProfile getUserProfile(String username){
		JSONObject jsonObj = new JSONObject();
		try {
			jsonObj.put("action", "profile");
			jsonObj.put("username", username);
			jsonObj.put("session", this.mSession);
		} catch (JSONException e1) {
			e1.printStackTrace();
		}
		JSONObject obj = post(REQUEST_PROFILE, jsonObj);
		if (obj == null) {
			return null;
		}
		String result = getString(obj, "result");
		if (result.equals("success")) {
			return new BuProfile(obj); 
		}
		return null;
	}
	
	/*
	 * 得到指定论坛的帖子
	 */
	public ArrayList<BuThread> getThreads(int fid, int from, int to) {
		ArrayList<BuThread> buThreads = new ArrayList<BuThread>();
		JSONObject jsonObj = new JSONObject();
		try {
			jsonObj.put("action", "thread");
			jsonObj.put("username", this.mUsername);
			jsonObj.put("session", this.mSession);
			jsonObj.put("fid", fid);
			jsonObj.put("from", from);
			jsonObj.put("to", to);
		} catch (JSONException e1) {
			e1.printStackTrace();
		}
		JSONObject obj = post(REQUEST_THREAD, jsonObj);
		if (obj == null) {
			return null;
		}
		String result = getString(obj, "result");
		if (result.equals("success")) {
			JSONArray data = getData(obj, "threadlist"); 
			if (data == null)
				return null;
			for (int i = 0; i < data.length(); i++) {
				buThreads.add(new BuThread((JSONObject) data.opt(i)));
			}
			return buThreads;
		}	
		return null;
	}

	/*
	 * 得到指定帖子的详细信息
	 */
	public ArrayList<BuPost> getPosts(String id, int from, int to) {
		ArrayList<BuPost> buPosts = new ArrayList<BuPost>();
		JSONObject jsonObj = new JSONObject();
		try {
			jsonObj.put("action", "post");
			jsonObj.put("username", this.mUsername);
			jsonObj.put("session", this.mSession);
			jsonObj.put("tid", id);
			jsonObj.put("from", from + "");
			jsonObj.put("to", to + "");
		} catch (JSONException e1) {
			e1.printStackTrace();
		}
		JSONObject obj = post(REQUEST_POST, jsonObj);
		if (obj == null) {
			return null;
		}
		String result = getString(obj, "result");
		if (result.equals("success")) {
			JSONArray data = getData(jsonObj, "postlist");
			if (data == null)
				return null;
			for (int i = 0; i < data.length(); i++) {
				buPosts.add(new BuPost((JSONObject) data.opt(i)));
			}
			return buPosts;
		}
		return null;
	}

	/*
	 * 发布新帖
	 */
	public RequestResult postThread(int fid, String subject, String message) {
		JSONObject jsonObj = new JSONObject();
		try {
			jsonObj.put("action", "newthread");
			jsonObj.put("username", this.mUsername);
			jsonObj.put("session", this.mSession);
			jsonObj.put("fid", fid);
			jsonObj.put("subject", URLEncoder.encode(subject, "UTF-8"));
			jsonObj.put("message", URLEncoder.encode(message, "UTF-8"));
			jsonObj.put("attachment", 1);
		} catch (JSONException e1) {
			e1.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return post2(NEWPOST, jsonObj);
	}

	/*
	 * 回复帖子
	 */
	public RequestResult replyThread(String tid, String message) {
		JSONObject jsonObj = new JSONObject();
		try {
			jsonObj.put("action", "newreply");
			jsonObj.put("username", this.mUsername);
			jsonObj.put("session", this.mSession);
			jsonObj.put("tid", tid);
			jsonObj.put("message", URLEncoder.encode(message, "UTF-8"));
			jsonObj.put("attachment", 1);
		} catch (JSONException e1) {
			e1.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return post2(NEWPOST, jsonObj);
	}

	/*
	 * post数据
	 */
	private JSONObject post(String url, JSONObject obj) {
		HttpPost httpPost = new HttpPost(url);
		String result = null;
		try {
			httpPost.setEntity(new StringEntity(obj.toString()));
			HttpResponse response = client.execute(httpPost);
			this.cookieStore = ((AbstractHttpClient) client).getCookieStore();
			if (response.getStatusLine().getStatusCode() == 200) {
				result = EntityUtils.toString(response.getEntity());

				return new JSONObject(result);
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			result = e.getMessage().toString();
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			result = e.getMessage().toString();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			result = e.getMessage().toString();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private RequestResult post2(String url, JSONObject obj) {
		HttpURLConnection conn = null;
		final String BOUNDARY = "----BitunionAndroidKit";
		String lineEnd = System.getProperty("line.separator");
		try {
			conn = (HttpURLConnection) new URL(url).openConnection();
			conn.setConnectTimeout(120000);
			// 发送POST请求必须设置如下两行
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setUseCaches(false);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Connection", "keep-alive");
			conn.setRequestProperty("Charset", "UTF-8");
			conn.setRequestProperty("Content-Type",
					"multipart/form-data; boundary=" + BOUNDARY);
			conn.connect();
			DataOutputStream stream = new DataOutputStream(
					conn.getOutputStream());
			StringBuffer resSB = new StringBuffer();
			// resSB.append("--" + BOUNDARY + lineEnd);
			// resSB.append("Content-Disposition: form-data; name=\"content\""
			// + lineEnd);
			// resSB.append(lineEnd);
			// resSB.append("test Bitunion Android Version" + lineEnd);

			resSB.append("--" + BOUNDARY + lineEnd);
			resSB.append("Content-Disposition: form-data; name=\"json\""
					+ lineEnd);
			resSB.append(lineEnd);
			resSB.append(obj.toString() + lineEnd);

			// resSB.append("--" + BOUNDARY + lineEnd);
			// resSB.append("Content-Disposition: form-data; name=\"attach\""
			// + lineEnd);
			// resSB.append(lineEnd);
			// resSB.append(lineEnd);

			resSB.append("--" + BOUNDARY + "--" + lineEnd);
			stream.writeBytes(resSB.toString());
			stream.flush();

			int code = conn.getResponseCode();

			if (code == 200) {
				InputStream in = conn.getInputStream();
				int ch;
				StringBuilder sb2 = new StringBuilder();
				while ((ch = in.read()) != -1) {
					sb2.append((char) ch);
				}
				return new RequestResult(new JSONObject(sb2.toString()));
			}
		} catch (MalformedURLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
		return null;
	}

	private JSONArray getData(JSONObject jsonObject, String dataName) {
		try {
			return jsonObject.getJSONArray(dataName);
		} catch (JSONException e) {
			return null;
		}
	}
	
	public static String getString(JSONObject jsonObject, String name) {
		try {
			return URLDecoder.decode(jsonObject.getString(name));
		} catch (JSONException e) {
			return "";
		}
	}

	public static int getInt(JSONObject jsonObject, String name) {
		try {
			return jsonObject.getInt(name);
		} catch (JSONException e) {
			return 0;
		}
	}

	public static String getTimeStr(JSONObject jsonObject, String name,
			String format) {
		try {
			String t = URLDecoder.decode(jsonObject.getString(name));
			return formatTime(t, format);
		} catch (JSONException e) {
			return "";
		}

	}

	private static String formatTime(String t, String format) {
		SimpleDateFormat dateFormat = new SimpleDateFormat(format);
		return dateFormat.format(new Date(Long.valueOf(t) * 1000L));
	}

	// 下载图片
	public InputStream getImageStream(String url) {
		if (mNetType == OUTNET) {
			url = url.replace("www.bitunion.org", "out.bitunion.org");
		}
		HttpGet httpGet = new HttpGet(url);
		HttpResponse response;
		try {
			httpGet.setHeader("Referer", "http://www.bitunion.org/");
			DefaultHttpClient client2 = new DefaultHttpClient();
			client2.setCookieStore(this.cookieStore);
			response = client2.execute(httpGet);
			if (response.getStatusLine().getStatusCode() == 200) {
				return response.getEntity().getContent();
			}
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public Cookie getCookie() {
		return mCookie;
	}

	public void setCookie(Cookie mCookie) {
		this.mCookie = mCookie;
	}

	public int getError() {
		return mError;
	}

	public int getNetType() {
		return mNetType;
	}

	public void setNetType(int net) {
		mNetType = net;
	}
}


class RequestResult {
	String result, msg;

	public RequestResult(JSONObject obj) {
		result = BitunionAPI.getString(obj, "result");
		msg = BitunionAPI.getString(obj, "msg");
	}

	public String getResult() {
		return result;
	}

	public String getMessage() {
		return msg;
	}
}