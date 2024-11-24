import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Gauge;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.ImageItem;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.Spacer;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import cc.nnproject.json.JSONStream;

public class Ran extends MIDlet implements CommandListener, ItemCommandListener, Runnable {
	
	private static final String SOCIAL_API_URL = "https://api.lib.social/api/";

	private static final int RUN_THUMBNAILS = 1;
	private static final int RUN_LIST = 2;
	private static final int RUN_MANGA = 3;
	private static final int RUN_CHAPTER = 4;
	
	private static final String SETTINGS_RMS = "ransets";

	private static final Font largefont = Font.getFont(0, 0, Font.SIZE_LARGE);
	private static final Font medfont = Font.getFont(0, 0, Font.SIZE_MEDIUM);
	private static final Font medboldfont = Font.getFont(0, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
	private static final Font medbolditalicfont = Font.getFont(0, Font.STYLE_BOLD | Font.STYLE_ITALIC, Font.SIZE_MEDIUM);
	private static final Font meditalicfont = Font.getFont(0, Font.STYLE_ITALIC, Font.SIZE_MEDIUM);
	private static final Font smallfont = Font.getFont(0, 0, Font.SIZE_SMALL);

	private static Ran midlet;
	private static Display display;
	
	private static Command exitCmd;
	private static Command settingsCmd;
	private static Command backCmd;
	private static Command searchCmd;
	private static Command latestCmd;
	
	private static Command mangaItemCmd;
	private static Command chapterItemCmd;
	
	private static Form mainForm;
	private static Form listForm;
	private static Form mangaForm;
	private static Form chapterForm;
	
	private static TextField searchField;
	
	private static int run;
	private static boolean running;
	
	private static String mangaId;
	private static String query;
	private static int listPage;
	private static Hashtable chapterItems;
	private static String chapterParams;
	
	private static Object thumbLoadLock = new Object();
	private static Vector thumbsToLoad = new Vector();
	
	// settings
	private static String proxyUrl = "http://nnp.nnchan.ru/hproxy.php?";
	private static boolean onlineResize = true;
	private static boolean useProxy = true;

	protected void destroyApp(boolean u) {}

	protected void pauseApp() {}

	protected void startApp() {
		if (midlet != null) return;
		midlet = this;
		
		display = Display.getDisplay(this);

		exitCmd = new Command("Выход", Command.EXIT, 2);
		searchCmd = new Command("Поиск", Command.ITEM, 1);
		settingsCmd = new Command("Настройки", Command.SCREEN, 3);
		
		backCmd = new Command("Назад", Command.EXIT, 2);
		mangaItemCmd = new Command("Открыть", Command.ITEM, 1);
		chapterItemCmd = new Command("Открыть", Command.ITEM, 1);
		latestCmd = new Command("Последнее", Command.ITEM, 1);
		
		Form f = new Form("RanobeLib");
		f.addCommand(exitCmd);
		f.addCommand(settingsCmd);
		f.setCommandListener(this);
		
		searchField = new TextField("", "", 100, TextField.ANY);
		searchField.addCommand(searchCmd);
		searchField.setItemCommandListener(this);
		f.append(searchField);
		
		StringItem s = new StringItem(null, "Поиск", Item.BUTTON);
		s.setLayout(Item.LAYOUT_EXPAND | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
		s.setDefaultCommand(searchCmd);
		s.setItemCommandListener(this);
		f.append(s);
		
		s = new StringItem(null, "Последнее", Item.BUTTON);
		s.setLayout(Item.LAYOUT_EXPAND | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
		s.setDefaultCommand(latestCmd);
		s.setItemCommandListener(this);
		f.append(s);
		
		display.setCurrent(mainForm = f);

		start(RUN_THUMBNAILS);
	}

	public void commandAction(Command c, Displayable d) {
		if (c == backCmd) {
			thumbsToLoad.removeAllElements();
			if (d == chapterForm) {
				display(mangaForm);
				chapterForm = null;
				return;
			}
			if (d == mangaForm) {
				display(listForm != null ? listForm : mainForm);
				mangaForm = null;
				return;
			}
			if (d == listForm) {
				listForm = null;
			}
			display(mainForm);
			return;
		}
		if (c == latestCmd) {
			if (running) return;
			
			query = null;
			listPage = 0;
			
			Form f = new Form("Последнее");
			f.addCommand(backCmd);
			f.setCommandListener(this);
			
			listForm = f;
			
			display(loadingAlert());
			start(RUN_LIST);
			return;
		}
		if (c == searchCmd) {
			if (running) return;
			
			query = searchField.getString();
			listPage = 0;
			
			Form f = new Form("Поиск");
			f.addCommand(backCmd);
			f.setCommandListener(this);
			
			listForm = f;
			
			display(loadingAlert());
			start(RUN_LIST);
			return;
		}
		if (c == exitCmd) {
			notifyDestroyed();
			return;
		}
	}

	public void commandAction(Command c, Item item) {
		if (c == mangaItemCmd) {
			if (running) return;
			thumbsToLoad.removeAllElements();
			
			Form f = new Form(((ImageItem) item).getLabel());
			f.addCommand(backCmd);
			f.setCommandListener(this);

			mangaId = ((ImageItem) item).getAltText();
			mangaForm = f;
			
			display(loadingAlert());
			start(RUN_MANGA);
			return;
		}
		if (c == chapterItemCmd) {
			if (running) return;
			String s = (String) chapterItems.get(item);
			if (s == null) return;
			
			Form f = new Form(s);
			f.addCommand(backCmd);
			f.setCommandListener(this);

			chapterParams = s;
			chapterForm = f;
			
			display(loadingAlert());
			start(RUN_CHAPTER);
			return;
		}
		commandAction(c, display.getCurrent());
	}

	public void run() {
		int run;
		synchronized(this) {
			run = Ran.run;
			notify();
		}
		if (running = run != RUN_THUMBNAILS)
			System.gc();
		switch (run) {
		case RUN_THUMBNAILS: { // background thumbnails loader thread
			try {
				while (true) {
					synchronized (thumbLoadLock) {
						thumbLoadLock.wait();
					}
					Thread.sleep(200);
					while (thumbsToLoad.size() > 0) {
						int i = 0;
						Object[] o = null;
						
						try {
							synchronized (thumbLoadLock) {
								o = (Object[]) thumbsToLoad.elementAt(i);
								thumbsToLoad.removeElementAt(i);
							}
						} catch (Exception e) {
							continue;
						}
						
						if (o == null) continue;
						
						String url = (String) o[0];
						ImageItem item = (ImageItem) o[1];
						
						if (url == null) continue;
						
						try { 
							Image img = getImage(proxyUrl(url.concat(";jpg;tw=180")));

//							int h = getHeight() / 3;
//							int w = (int) (((float) h / img.getHeight()) * img.getWidth());
//							img = resize(img, w, h);
							
							item.setImage(img);
						} catch (Exception e) {
							e.printStackTrace();
						} 
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return;
		}
		case RUN_LIST: {
			Form f = listForm;
			f.deleteAll();
			try {
				StringBuffer sb = new StringBuffer("manga?site_id[]=3&sort_by=last_chapter_at");
				if (query != null) sb.append("&q=").append(url(query));
				if (listPage > 0) sb.append("&page=").append(listPage);
				
				JSONArray j = (JSONArray) api(sb.toString());
				int l = j.size();
				
				for (int i = 0; i < l; ++i) {
					JSONObject v = j.getObject(i);
					
					ImageItem item = new ImageItem(v.getString("rus_name", v.getString("name")),
							null,
							Item.LAYOUT_EXPAND | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE,
							v.getString("slug"));
					item.setDefaultCommand(mangaItemCmd);
					item.setItemCommandListener(this);
					
					JSONObject cover = v.getObject("cover");
					
					synchronized (thumbLoadLock) {
						thumbsToLoad.addElement(new Object[] { cover.getString("default", cover.getString("thumbnail")), item });
						thumbLoadLock.notifyAll();
					}
					
					f.append(item);
				}
				
				
				if (listForm == f)
					display(f);
			} catch (Exception e) {
				e.printStackTrace();
				if (listForm == f)
					display(errorAlert(e.toString()), f);
			}
			break;
		}
		case RUN_MANGA: {
			Form f = mangaForm;
			StringItem s;
			try {
				StringBuffer sb = new StringBuffer("manga/").append(mangaId);
				JSONObject j = (JSONObject) api(sb.toString());
				
				s = new StringItem(null, j.getString("rus_name", j.getString("name")));
				s.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
				f.append(s);
				// TODO
				
				chapterItems = new Hashtable();
				
				JSONArray chapters = (JSONArray) api(sb.append("/chapters").toString());
				int l = chapters.size();
				for (int i = 0; i < l; ++i) {
					JSONObject chapter = chapters.getObject(i);
					String vol = chapter.getString("volume");
					String num = chapter.getString("number");
					String name = chapter.getString("name", "");
					int branchesCount = chapter.getInt("branches_count");
					
					sb.setLength(0);
					sb.append("Том ").append(vol).append(" Глава ").append(num);
					if (name.length() > 0) sb.append(" - ").append(name);
					s = new StringItem(null, sb.toString());
					
					sb.setLength(0);
					sb.append("volume=").append(vol).append("&number=").append(num);
					
					if (branchesCount == 1) {
						s.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
						s.setDefaultCommand(chapterItemCmd);
						s.setItemCommandListener(this);
						f.append(s);
						chapterItems.put(s, sb.toString());
					} else {
						f.append(s);
						JSONArray branches = chapter.getArray("branches");
						for (int k = 0; k < branchesCount; ++k) {
							JSONObject branch = branches.getObject(k);
							
							String t;
							if (branch.has("teams") && branch.getArray("teams").size() != 0) {
								t = branch.getArray("teams").getObject(0).getString("name");
							} else {
								t = branch.getObject("user").getString("username");
							}
							s = new StringItem(null, " - ".concat(t));
							System.out.println(t);
							s.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
							s.setDefaultCommand(chapterItemCmd);
							s.setItemCommandListener(this);
							f.append(s);
							chapterItems.put(s, sb.append("&branch_id=").append(branch.getString("branch_id")).toString());
						}
					}
				}
				
				if (mangaForm == f)
					display(f);
			} catch (Exception e) {
				e.printStackTrace();
				if (mangaForm == f)
					display(errorAlert(e.toString()), f);
			}
			break;
		}
		case RUN_CHAPTER: {
			Form f = chapterForm;
			try {
				StringBuffer sb = new StringBuffer("manga/").append(mangaId)
						.append("/chapter?").append(chapterParams);
				JSONObject j = (JSONObject) api(sb.toString());
				
				Object content = j.get("content");
				if (content instanceof String) {
					// TODO html parse
					f.append((String) content);
				} else {
					String type = ((JSONObject) content).getString("type");
					if ("doc".equals(type)) {
						parseJsonContent(f, ((JSONObject) content).getArray("content"));
					} else {
						// unknown
						f.append(content.toString());
					}
				}
				
				if (chapterForm == f)
					display(f);
			} catch (Exception e) {
				e.printStackTrace();
				if (chapterForm == f)
					display(errorAlert(e.toString()), f);
			}
			break;
		}
		}
		running = false;
	}

	private void parseJsonContent(Form f, JSONArray content) {
		int l = content.size();
		for (int i = 0; i < l; ++i) {
			JSONObject e = content.getObject(i);
			String type = e.getString("type");
			
			if ("paragraph".equals(type)) {
				if (e.has("content")) parseJsonContent(f, e.getArray("content"));
				
				Spacer s = new Spacer(8, smallfont.getHeight());
				s.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
				f.append(s);
			} else if ("text".equals(type)) {
				StringItem s = new StringItem(null, e.getString("text"));
				Font font = medfont;
				if (e.has("marks")) {
					
					// TODO marks:[{type:asd}] bold,italic
				}
				s.setFont(font);
				f.append(s);
			} else if ("image".equals(type)) {
				// TODO attrs:[images:[{image:id}]]
				f.append(new ImageItem("Image", null, 0, null));
			} else if ("listItem".equals(type)) {
				// TODO
				if (e.has("content")) parseJsonContent(f, e.getArray("content"));
			}
		}
	}

	void start(int i) {
		try {
			synchronized(this) {
				run = i;
				new Thread(this).start();
				wait();
			}
		} catch (Exception e) {}
	}
	
	static void display(Alert a, Displayable d) {
		if(d == null) {
			display.setCurrent(a);
			return;
		}
		display.setCurrent(a, d);
	}

	static void display(Displayable d) {
		if(d instanceof Alert) {
			display.setCurrent((Alert) d, mainForm);
			return;
		}
		if (d == null)
			d = mainForm;
		display.setCurrent(d);
	}

	private static Alert errorAlert(String text) {
		Alert a = new Alert("");
		a.setType(AlertType.ERROR);
		a.setString(text);
		a.setTimeout(2000);
		return a;
	}
	
	private static Alert loadingAlert() {
		Alert a = new Alert("", "Загрузка...", null, null);
		a.setCommandListener(midlet);
		a.addCommand(Alert.DISMISS_COMMAND);
		a.setIndicator(new Gauge(null, false, Gauge.INDEFINITE, Gauge.CONTINUOUS_RUNNING));
		a.setTimeout(30000);
		return a;
	}
	
	private static Object api(String url) throws IOException {
		Object res;

		HttpConnection hc = null;
		InputStream in = null;
		try {
			hc = open(proxyUrl(SOCIAL_API_URL.concat(url)));
			hc.setRequestMethod("GET");
			hc.setRequestProperty("Origin", "https://ranobelib.me");
			hc.setRequestProperty("Referrer", "https://ranobelib.me");
			int c;
			if ((c = hc.getResponseCode()) >= 400) {
				throw new IOException("HTTP ".concat(Integer.toString(c)));
			}
			res = JSONStream.getStream(in = hc.openInputStream()).nextValue();
			System.out.println(((JSONObject) res).format(0));
			if (((JSONObject) res).has("data"))
				res = ((JSONObject) res).get("data");
		} finally {
			if (in != null) try {
				in.close();
			} catch (IOException e) {}
			if (hc != null) try {
				hc.close();
			} catch (IOException e) {}
		}
//		System.out.println(res);
		return res;
	}

	private static Image getImage(String url) throws IOException {
		byte[] b = get(url);
		return Image.createImage(b, 0, b.length);
	}
	
	private static byte[] readBytes(InputStream inputStream, int initialSize, int bufferSize, int expandSize) throws IOException {
		if (initialSize <= 0) initialSize = bufferSize;
		byte[] buf = new byte[initialSize];
		int count = 0;
		byte[] readBuf = new byte[bufferSize];
		int readLen;
		while ((readLen = inputStream.read(readBuf)) != -1) {
			if(count + readLen > buf.length) {
				byte[] newbuf = new byte[count + expandSize];
				System.arraycopy(buf, 0, newbuf, 0, count);
				buf = newbuf;
			}
			System.arraycopy(readBuf, 0, buf, count, readLen);
			count += readLen;
		}
		if(buf.length == count) {
			return buf;
		}
		byte[] res = new byte[count];
		System.arraycopy(buf, 0, res, 0, count);
		return res;
	}
	
	private static String readUtf(InputStream in, int i) throws IOException {
		byte[] buf = new byte[i <= 0 ? 16384 : i];
		i = 0;
		int j;
		while((j = in.read(buf, i, buf.length - i)) != -1) {
			if ((i += j) >= buf.length) {
				System.arraycopy(buf, 0, buf = new byte[i + 4096], 0, i);
			}
		}
		return new String(buf, 0, i, "UTF-8");
	}
	
	private static byte[] get(String url) throws IOException {
		HttpConnection hc = null;
		InputStream in = null;
		try {
			hc = open(url);
			hc.setRequestMethod("GET");
			int r;
			if((r = hc.getResponseCode()) >= 400) {
				throw new IOException("HTTP " + r);
			}
			in = hc.openInputStream();
			return readBytes(in, (int) hc.getLength(), 8*1024, 16*1024);
		} finally {
			try {
				if (in != null) in.close();
			} catch (IOException e) {
			}
			try {
				if (hc != null) hc.close();
			} catch (IOException e) {
			}
		}
	}
	
	private static HttpConnection open(String url) throws IOException {
		HttpConnection hc = (HttpConnection) Connector.open(url);
		hc.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0");
		return hc;
	}
	
	private static String proxyUrl(String url) {
		System.out.println(url);
		if (url == null
				|| (!useProxy && (url.indexOf(";tw=") == -1 || !onlineResize))
				|| proxyUrl == null || proxyUrl.length() == 0 || "https://".equals(proxyUrl)) {
			return url;
		}
		return proxyUrl + url(url);
	}
	
	public static String url(String url) {
		StringBuffer sb = new StringBuffer();
		char[] chars = url.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			int c = chars[i];
			if (65 <= c && c <= 90) {
				sb.append((char) c);
			} else if (97 <= c && c <= 122) {
				sb.append((char) c);
			} else if (48 <= c && c <= 57) {
				sb.append((char) c);
			} else if (c == 32) {
				sb.append("%20");
			} else if (c == 45 || c == 95 || c == 46 || c == 33 || c == 126 || c == 42 || c == 39 || c == 40
					|| c == 41) {
				sb.append((char) c);
			} else if (c <= 127) {
				sb.append(hex(c));
			} else if (c <= 2047) {
				sb.append(hex(0xC0 | c >> 6));
				sb.append(hex(0x80 | c & 0x3F));
			} else {
				sb.append(hex(0xE0 | c >> 12));
				sb.append(hex(0x80 | c >> 6 & 0x3F));
				sb.append(hex(0x80 | c & 0x3F));
			}
		}
		return sb.toString();
	}

	private static String hex(int i) {
		String s = Integer.toHexString(i);
		return "%".concat(s.length() < 2 ? "0" : "").concat(s);
	}

}
