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
import javax.microedition.lcdui.Ticker;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;

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

	private static final Font largePlainFont = Font.getFont(0, 0, Font.SIZE_LARGE);
	private static final Font medPlainFont = Font.getFont(0, 0, Font.SIZE_MEDIUM);
	private static final Font medBoldFont = Font.getFont(0, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
	private static final Font medItalicFont = Font.getFont(0, Font.STYLE_ITALIC, Font.SIZE_MEDIUM);
	private static final Font medItalicBoldFont = Font.getFont(0, Font.STYLE_BOLD | Font.STYLE_ITALIC, Font.SIZE_MEDIUM);
	private static final Font smallPlainFont = Font.getFont(0, 0, Font.SIZE_SMALL);
	private static final Font smallBoldFont = Font.getFont(0, Font.STYLE_BOLD, Font.SIZE_SMALL);
	private static final Font smallItalicFont = Font.getFont(0, Font.STYLE_ITALIC, Font.SIZE_SMALL);

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
	private static Form settingsForm;
	
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
	private static String proxyUrl = null;
	private static boolean onlineResize = false;
	private static boolean useProxy = true;
	private static boolean showChapterWhileParsing = true;

	protected void destroyApp(boolean u) {}

	protected void pauseApp() {}

	protected void startApp() {
		if (midlet != null) return;
		midlet = this;
		
		display = Display.getDisplay(this);
		
		try {
			RecordStore r = RecordStore.openRecordStore(SETTINGS_RMS, false);
			JSONObject j = JSONObject.parseObject(new String(r.getRecord(1), "UTF-8"));
			r.closeRecordStore();
			
			proxyUrl = j.getString("proxy", proxyUrl);
			onlineResize = j.getBoolean("onlineResize", onlineResize);
			useProxy = j.getBoolean("useProxy", useProxy);
			showChapterWhileParsing = j.getBoolean("showChapterWhileParsing", showChapterWhileParsing);
		} catch (Exception e) {}

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
			} else if (d == settingsForm) {
				// TODO
				try {
					RecordStore.deleteRecordStore(SETTINGS_RMS);
				} catch (Exception e) {}
				try {
					JSONObject j = new JSONObject();
					j.put("proxy", proxyUrl);
					j.put("onlineResize", onlineResize);
					j.put("useProxy", useProxy);
					j.put("showChapterWhileParsing", showChapterWhileParsing);
					
					byte[] b = j.toString().getBytes("UTF-8");
					RecordStore r = RecordStore.openRecordStore(SETTINGS_RMS, true);
					r.addRecord(b, 0, b.length);
					r.closeRecordStore();
				} catch (Exception e) {}
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
			
			display(loadingAlert(), listForm);
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
			
			display(loadingAlert(), listForm);
			start(RUN_LIST);
			return;
		}
		if (c == settingsCmd) {
			if (settingsForm == null) {
				Form f = settingsForm = new Form("Настройки");
				f.addCommand(backCmd);
				f.setCommandListener(this);
				// TODO
			}
			display(settingsForm);
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
			
			display(loadingAlert(), mangaForm);
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
			
			display(loadingAlert(), mangaForm);
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
							Image img = getImage(proxyUrl(url));

							int h = getHeight() / 3;
							int w = (int) (((float) h / img.getHeight()) * img.getWidth());
							img = resize(img, w, h);
							
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
				StringBuffer sb = new StringBuffer("manga?site_id%5B%5D=3&sort_by=last_chapter_at");
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
						s.setFont(smallBoldFont);
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
							s.setFont(smallPlainFont);
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
				Alert a = loadingAlert();
				Gauge gauge = null;
				display(a);
				
				a.setString("Скачивание..");
				StringBuffer sb = new StringBuffer("manga/").append(mangaId)
						.append("/chapter?").append(chapterParams);
				JSONObject j = (JSONObject) api(sb.toString());
				
				sb.setLength(0);
				sb.append("Том ")
				.append(j.getString("volume"))
				.append(" Глава ")
				.append(j.getString("number"));
				f.setTitle(sb.toString());

				if (showChapterWhileParsing) {
					f.setTicker(new Ticker("Парсинг.."));
					display(f);
				} else {
					a.setString("Парсинг..");
					gauge = a.getIndicator();
				}
				
				Object content = j.get("content");
				if (content instanceof String) {
					parseHtmlContent2(f, (String) content, gauge);
//					if (tidy == null) tidy = new Tidy();
//					parseHtmlContent(f, tidy.parseDOM("<html>".concat((String) content).concat("</html>")).getDocumentElement().getChildNodes());
				} else {
					String type = ((JSONObject) content).getString("type");
					if ("doc".equals(type)) {
						parseJsonContent(f, ((JSONObject) content).getArray("content"), gauge);
					} else {
						// unknown
						f.append(content.toString());
					}
				}

				if (gauge != null) {
					a.setString("Загрузка формы..");
					gauge.setMaxValue(Gauge.INDEFINITE);
					gauge.setValue(Gauge.CONTINUOUS_RUNNING);
				}
				f.setTicker(null);
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

	private static void parseJsonContent(Form f, JSONArray content, Gauge gauge) {
		int l = content.size();
		if (l == 0) return;
		if (gauge != null) gauge.setMaxValue(l);
		for (int i = 0; i < l; ++i) {
			JSONObject e = content.getObject(i);
			String type = e.getString("type");
			
			if ("paragraph".equals(type)) {
				f.append("\n");
				if (e.has("content")) parseJsonContent(f, e.getArray("content"), null);
				
//				Spacer s = new Spacer(8, smallPlainFont.getHeight());
//				s.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
//				f.append(s);

				f.append("\n");
			} else if ("text".equals(type)) {
				StringItem s = new StringItem(null, e.getString("text"));
				int style = 0;
				if (e.has("marks")) {
					JSONArray marks = e.getArray("marks");
					int k = marks.size();
					for (int m = 0; m < k; ++m) {
						String t = marks.getObject(m).getString("type");
						if ("italic".equals(t)) {
							style |= Font.STYLE_ITALIC;
						} else if ("bold".equals(t)) {
							style |= Font.STYLE_BOLD;
						}
					}
					// TODO marks:[{type:asd}] bold,italic
				}
				s.setFont(getFont(0, style, Font.SIZE_MEDIUM));
				f.append(s);
			} else if ("image".equals(type)) {
				// TODO attrs:[images:[{image:id}]]
//				String url = e.getArray("attrs").getObject(0).getArray("images").getObject(0).getString("image");
				f.append(new ImageItem("Картинка", null, 0, null));
			} else if ("listItem".equals(type)) {
				// TODO
				if (e.has("content")) parseJsonContent(f, e.getArray("content"), null);
			}
			if (gauge != null) gauge.setValue(i);
		}
	}
	
	private static void parseHtmlContent2(Form f, String src, Gauge gauge) {
		int d = src.indexOf('<');
		int len = src.length();
		if (len == 0) return;
		int o = 0;
		StringItem s;
		StringBuffer sb = new StringBuffer();
		int fstyle = Font.STYLE_PLAIN;
		int fsize = Font.SIZE_MEDIUM;
		String tag;
		int spW = medPlainFont.charWidth(' ') + 1, spH = medPlainFont.getHeight();
		
		System.out.println("len: " + len);
		gauge.setMaxValue(202);
		char[] chars = src.toCharArray();
		while (d != -1) {
			if (o != d) {
				char l = 0;
				for (int i = o; i < d; ++i) {
					char c = chars[i];
					if (c == '\r' || c == '\n' || c == '\t') {
						c = ' ';
					} else if (c == '&') {
						char t;
						if ((t = chars[++i]) == 'n') { // nbsp
							if (chars[i += 4] == ';') {
								c = ' ';
							} else i -= 5;
						} else if (t == 'l') { // lt
							if (chars[i += 2] == ';') {
								c = '<';
							} else i -= 3;
						} else if (t == 'g') { // gt
							if (chars[i += 2] == ';') {
								c = '>';
							} else i -= 3;
							c = '>';
						} else i--;
					}
					if (c == ' ' && l == ' ') {
						continue;
					}
					l = c;
					sb.append(c);
				}
				if (sb.length() != 0) {
					s = new StringItem(null, sb.toString());
					s.setFont(getFont(0, fstyle, fsize));
					f.append(s);
//					if (chars[d - 1] == ' ')
//						f.append(new Spacer(spW, spH));
					
					sb.setLength(0);
				}
			}
			int e = src.indexOf('>', d);
			if (chars[d + 1] == '/') {
				tag = src.substring(d + 2, e);
				if ("b".equals(tag) || "strong".equals(tag)) {
					fstyle &= ~Font.STYLE_BOLD;
				} else if ("h1".equals(tag)) {
					fsize = Font.SIZE_MEDIUM;
					fstyle &= ~Font.STYLE_BOLD;
				} else if ("h2".equals(tag)) {
					fsize = Font.SIZE_MEDIUM;
				} else if ("em".equals(tag) || "i".equals(tag)) {
					fstyle &= ~Font.STYLE_ITALIC;
				} else if ("small".equals(tag)) {
					fsize = Font.SIZE_MEDIUM;
				} else if ("sub".equals(tag)) {
					fstyle &= ~Font.STYLE_UNDERLINED;
				} else if ("p".equals(tag)) {
					sb.append("\n");
				}
			} else {
				tag = src.substring(d + 1, e);
				if ((chars[d + 1] == 'b' && chars[d + 2] == '>')
						|| (chars[d + 1] == 's' && chars[d + 2] == 't')) {
					fstyle |= Font.STYLE_BOLD;
				} else if (chars[d + 1] == 'h') {
					fsize = Font.SIZE_LARGE;
					if (chars[d + 2] == '1') fstyle |= Font.STYLE_BOLD;
				} else if ((chars[d + 1] == 'e' && chars[d + 2] == 'm')
						|| (chars[d + 1] == 'i' && chars[d + 2] == '>')) {
					fstyle |= Font.STYLE_ITALIC;
				} else if (chars[d + 1] == 's' && chars[d + 2] == 'm') {
					fsize = Font.SIZE_SMALL;
				} else if (chars[d + 1] == 's' && chars[d + 2] == 'u') {
					fstyle |= Font.STYLE_UNDERLINED;
				} else if (chars[d + 1] == 'p'
						|| (chars[d + 1] == 'b' && chars[d + 2] == 'r')) {
					sb.append("\n");
				} else if (chars[d + 1] == 'i' && chars[d + 2] == 'm') {
					// TODO
					f.append(new ImageItem("Картинка", null, 0, null));
				}
			}
			d = src.indexOf('<', o = e + 1);
			if (d != -1 && gauge != null) {
				gauge.setValue((d*200)/len);
			}
		}
		chars = null;
		
		if (o < len) {
			s = new StringItem(null, src.substring(o + 1));
			s.setFont(medPlainFont);
			f.append(s);
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
	
	private static int getHeight() {
		return mainForm.getHeight();
	}
	
	static void display(Alert a, Displayable d) {
		if (d == null) {
			display.setCurrent(a);
			return;
		}
		display.setCurrent(a, d);
	}

	static void display(Displayable d) {
		if (d instanceof Alert) {
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

	private static Font getFont(int i, int j, int k) {
		if (i == 0) {
			if (k == Font.SIZE_SMALL) {
				if (j == Font.STYLE_BOLD) {
					return smallBoldFont;
				}
				if (j == Font.STYLE_ITALIC) {
					return smallItalicFont;
				}
				if (j == Font.STYLE_PLAIN) {
					return smallPlainFont;
				}
			}
			if (k == Font.SIZE_MEDIUM) {
				if (j == Font.STYLE_BOLD) {
					return medBoldFont;
				}
				if (j == Font.STYLE_ITALIC) {
					return medItalicFont;
				}
				if (j == (Font.STYLE_BOLD | Font.STYLE_ITALIC)) {
					return medItalicBoldFont;
				}
				if (j == Font.STYLE_PLAIN) {
					return medPlainFont;
				}
			}
			if (k == Font.SIZE_LARGE) {
				return largePlainFont;
			}
		}
		return Font.getFont(i, j, k);
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
//			res = JSONObject.parseObject(readUtf(in = hc.openInputStream(), (int) hc.getLength()));
//			System.out.println(((JSONObject) res).format(0));
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
			if (count + readLen > buf.length) {
				byte[] newbuf = new byte[count + expandSize];
				System.arraycopy(buf, 0, newbuf, 0, count);
				buf = newbuf;
			}
			System.arraycopy(readBuf, 0, buf, count, readLen);
			count += readLen;
		}
		if (buf.length == count) {
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
				System.arraycopy(buf, 0, buf = new byte[i + 16384], 0, i);
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
			if ((r = hc.getResponseCode()) >= 400) {
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

	static Image resize(Image src_i, int size_w, int size_h) {
		// set source size
		int w = src_i.getWidth();
		int h = src_i.getHeight();

		// no change??
		if (size_w == w && size_h == h)
			return src_i;
		
//		if (MangaApp.mipmap) {
//			while (w > size_w * 3 && h > size_h * 3) {
//				src_i = halve(src_i);
//				w /= 2;
//				h /= 2;
//			}
//		}

		int[] dst = new int[size_w * size_h];

		resize_rgb_filtered(src_i, dst, w, h, size_w, size_h);

		// not needed anymore
		src_i = null;

		return Image.createRGBImage(dst, size_w, size_h, true);
	}
	
	public static Image halve(Image org) {
		int w1 = org.getWidth();
		int h1 = org.getHeight();
		
		int w2 = w1 / 2;
		int h2 = h1 / 2;				
		
		int [] data = new int[w2 * h2];
		int [] buffer = new int[w1 * 2];
		
		for(int offset = 0, i = 0; i < h2; i++) {
			org.getRGB( buffer, 0, w1, 0, i * 2, w1, 2); // get two lines from the original
			
			int o1 = 0, o2 = 1;
			int o3 = w1, o4 = w1 + 1;
			
			for(int j = 0; j < w2; j++) {
				data[offset ++] = ((
						((buffer[o1] & 0x00FF00FF) + (buffer[o2] & 0x00FF00FF) + (buffer[o3] & 0x00FF00FF) + (buffer[o4] & 0x00FF00FF)) >> 2
						) & 0x00FF00FF) | ((
						((buffer[o1] & 0xFF00FF00) >>> 2) + ((buffer[o2] & 0xFF00FF00) >>> 2) + 
						((buffer[o3] & 0xFF00FF00) >>> 2) + ((buffer[o4] & 0xFF00FF00) >>> 2) 
						) & 0xFF00FF00);
						//mix( buffer[o1], buffer[o2], buffer[o3], buffer[o4]);			
				o1 += 2;
				o2 += 2;
				o3 += 2;
				o4 += 2;
			}
		}
		
		Image tmp = Image.createRGBImage(data, w2, h2, true);
		data = null; // can this help GC at this point?
		
		return tmp;
	}

	private static final void resize_rgb_filtered(Image src_i, int[] dst, int w0, int h0, int w1, int h1) {
		int[] buffer1 = new int[w0];
		int[] buffer2 = new int[w0];

		// UNOPTIMIZED bilinear filtering:
		//
		// The pixel position is defined by y_a and y_b,
		// which are 24.8 fixed point numbers
		// 
		// for bilinear interpolation, we use y_a1 <= y_a <= y_b1
		// and x_a1 <= x_a <= x_b1, with y_d and x_d defining how long
		// from x/y_b1 we are.
		//
		// since we are resizing one line at a time, we will at most 
		// need two lines from the source image (y_a1 and y_b1).
		// this will save us some memory but will make the algorithm 
		// noticeably slower

		for (int index1 = 0, y = 0; y < h1; y++) {

			final int y_a = ((y * h0) << 8) / h1;
			final int y_a1 = y_a >> 8;
			int y_d = y_a & 0xFF;

			int y_b1 = y_a1 + 1;
			if (y_b1 >= h0) {
				y_b1 = h0 - 1;
				y_d = 0;
			}

			// get the two affected lines:
			src_i.getRGB(buffer1, 0, w0, 0, y_a1, w0, 1);
			if (y_d != 0)
				src_i.getRGB(buffer2, 0, w0, 0, y_b1, w0, 1);

			for (int x = 0; x < w1; x++) {
				// get this and the next point
				int x_a = ((x * w0) << 8) / w1;
				int x_a1 = x_a >> 8;
				int x_d = x_a & 0xFF;

				int x_b1 = x_a1 + 1;
				if (x_b1 >= w0) {
					x_b1 = w0 - 1;
					x_d = 0;
				}

				// interpolate in x
				int c12, c34;
				int c1 = buffer1[x_a1];
				int c3 = buffer1[x_b1];

				// interpolate in y:
				if (y_d == 0) {
					c12 = c1;
					c34 = c3;
				} else {
					int c2 = buffer2[x_a1];
					int c4 = buffer2[x_b1];

					final int v1 = y_d & 0xFF;
					final int a_c2_RB = c1 & 0x00FF00FF;
					final int a_c2_AG_org = c1 & 0xFF00FF00;

					final int b_c2_RB = c3 & 0x00FF00FF;
					final int b_c2_AG_org = c3 & 0xFF00FF00;

					c12 = (a_c2_AG_org + ((((c2 >>> 8) & 0x00FF00FF) - (a_c2_AG_org >>> 8)) * v1)) & 0xFF00FF00
							| (a_c2_RB + ((((c2 & 0x00FF00FF) - a_c2_RB) * v1) >> 8)) & 0x00FF00FF;
					c34 = (b_c2_AG_org + ((((c4 >>> 8) & 0x00FF00FF) - (b_c2_AG_org >>> 8)) * v1)) & 0xFF00FF00
							| (b_c2_RB + ((((c4 & 0x00FF00FF) - b_c2_RB) * v1) >> 8)) & 0x00FF00FF;
				}

				// final result

				final int v1 = x_d & 0xFF;
				final int c2_RB = c12 & 0x00FF00FF;

				final int c2_AG_org = c12 & 0xFF00FF00;
				dst[index1++] = (c2_AG_org + ((((c34 >>> 8) & 0x00FF00FF) - (c2_AG_org >>> 8)) * v1)) & 0xFF00FF00
						| (c2_RB + ((((c34 & 0x00FF00FF) - c2_RB) * v1) >> 8)) & 0x00FF00FF;
			}
		}
	}

}
