import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.ChoiceGroup;
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
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.lcdui.Ticker;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;

import com.nokia.mid.ui.DeviceControl;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import cc.nnproject.json.JSONStream;
import zip.GZIPInputStream;
import zip.Inflater;
import zip.InflaterInputStream;

public class Ran extends MIDlet implements CommandListener, ItemCommandListener, Runnable {
	
	private static final String SOCIAL_API_URL = "https://api.cdnlibs.org/api/";

	private static final int RUN_THUMBNAILS = 1;
	private static final int RUN_LIST = 2;
	private static final int RUN_MANGA = 3;
	private static final int RUN_CHAPTER = 4;
	private static final int RUN_REPAINT = 5;
	
	private static final String SETTINGS_RMS = "ransets";

	static final Font largePlainFont = Font.getFont(0, 0, Font.SIZE_LARGE);
	static final Font medPlainFont = Font.getFont(0, 0, Font.SIZE_MEDIUM);
	static final Font medBoldFont = Font.getFont(0, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
	static final Font medItalicFont = Font.getFont(0, Font.STYLE_ITALIC, Font.SIZE_MEDIUM);
	static final Font medItalicBoldFont = Font.getFont(0, Font.STYLE_BOLD | Font.STYLE_ITALIC, Font.SIZE_MEDIUM);
	static final Font smallPlainFont = Font.getFont(0, 0, Font.SIZE_SMALL);
	static final Font smallBoldFont = Font.getFont(0, Font.STYLE_BOLD, Font.SIZE_SMALL);
	static final Font smallItalicFont = Font.getFont(0, Font.STYLE_ITALIC, Font.SIZE_SMALL);

	static Ran midlet;
	private static Display display;
	static boolean exiting;
	
	private static Command exitCmd;
	private static Command settingsCmd;
	static Command backCmd;
	private static Command searchCmd;
	private static Command latestCmd;
	
	private static Command mangaItemCmd;
	private static Command chapterItemCmd;
	
	private static Command brightnessCmd;

	private static Command okCmd;
	private static Command cancelCmd;
	
	private static Form mainForm;
	private static Form listForm;
	private static Form mangaForm;
	private static Displayable chapterForm;
	private static Form settingsForm;
	
	private static TextField searchField;

	private static TextField proxyField;
	private static ChoiceGroup proxyChoice;
	private static ChoiceGroup coversChoice;
	private static ChoiceGroup chapterSetsChoice;
	private static ChoiceGroup fontSizeChoice;
	
	private static int run;
	private static boolean running;
	
	private static String mangaId;
	private static String query;
	private static int listPage;
	private static Hashtable chapterItems;
	private static String chapterParams;
	private static JSONArray chapterAttachments;
	
	private static Object thumbLoadLock = new Object();
	private static Vector thumbsToLoad = new Vector();
	
	// settings
	private static String proxyUrl = "http://nnproject.cc/hproxy.php?";
	private static boolean onlineResize = true;
	private static boolean useProxy = false;
	private static int thumbLoading;
	private static boolean showChapterWhileParsing = true;
	private static boolean noFormat;
	private static boolean loadChapterImages;
	private static boolean loadCovers = true;
	private static int fontSize = 1;
	private static int brightness = -1;
	private static boolean useCanvas = true;
	private static boolean compress;
	
	private static boolean symbianJrt;
	private static boolean hasNokiaUi;

	private static Object repaintLock = new Object();
	private static boolean painterRunning;
	private static boolean repaint;
	static int repaintTime;

	protected void destroyApp(boolean u) {
		exiting = true;
	}

	protected void pauseApp() {}

	protected void startApp() {
		if (midlet != null) return;
		midlet = this;
		
		display = Display.getDisplay(this);
		
		Form f = new Form("RanobeLib");
		display.setCurrent(f);
		
		String p = System.getProperty("microedition.platform");
		symbianJrt = p != null && p.indexOf("platform=S60") != -1;
		
		try {
			Class.forName("com.nokia.mid.ui.DeviceControl");
			hasNokiaUi = true;
		} catch (Throwable e) {}
		
		try {
			RecordStore r = RecordStore.openRecordStore(SETTINGS_RMS, false);
			JSONObject j = JSONObject.parseObject(new String(r.getRecord(1), "UTF-8"));
			r.closeRecordStore();
			
			proxyUrl = j.getString("proxy", proxyUrl);
			useProxy = j.getBoolean("useProxy", useProxy);
			onlineResize = j.getBoolean("onlineResize", onlineResize);
			thumbLoading = j.getInt("thumbLoading", thumbLoading);
			showChapterWhileParsing = j.getBoolean("showChapterWhileParsing", showChapterWhileParsing);
			noFormat = j.getBoolean("noFormat", noFormat);
			loadChapterImages = j.getBoolean("loadChapterImages", loadChapterImages);
			loadCovers = j.getBoolean("loadCovers", loadCovers);
			fontSize = j.getInt("fontSize", fontSize);
			brightness = j.getInt("brightness", brightness);
		} catch (Exception e) {}

		exitCmd = new Command("Выход", Command.EXIT, 2);
		searchCmd = new Command("Поиск", Command.ITEM, 1);
		settingsCmd = new Command("Настройки", Command.SCREEN, 3);
		
		backCmd = new Command("Назад", Command.EXIT, 2);
		mangaItemCmd = new Command("Открыть", Command.ITEM, 1);
		chapterItemCmd = new Command("Открыть", Command.ITEM, 1);
		latestCmd = new Command("Последнее", Command.ITEM, 1);
		
		brightnessCmd = new Command("Яркость", Command.SCREEN, 2);
		
		okCmd = new Command("Ок", Command.OK, 1);
		cancelCmd = new Command("Отмена", Command.CANCEL, 2);
		
		f = new Form("RanobeLib");
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
		
		if (thumbLoading != 1 && (symbianJrt || thumbLoading == 2)) {
			start(RUN_THUMBNAILS);
		}
		
		if (hasNokiaUi && brightness != -1) {
			setLight(brightness);
		}
	}

	public void commandAction(Command c, Displayable d) {
		if (c == backCmd) {
			thumbsToLoad.removeAllElements();
			if (d == chapterForm) {
				display(mangaForm, true);
				chapterForm = null;
				chapterAttachments = null;
				return;
			}
			if (d == mangaForm) {
				display(listForm != null ? listForm : mainForm, true);
				mangaForm = null;
				return;
			}
			if (d == listForm) {
				listForm = null;
			} else if (d == settingsForm) {
				proxyUrl = proxyField.getString();
				useProxy = proxyChoice.isSelected(0);
				onlineResize = proxyChoice.isSelected(1);
				thumbLoading = coversChoice.getSelectedIndex();
				showChapterWhileParsing = chapterSetsChoice.isSelected(0);
				noFormat = chapterSetsChoice.isSelected(1);
				loadChapterImages = chapterSetsChoice.isSelected(2);
				loadCovers = chapterSetsChoice.isSelected(3);
				fontSize = fontSizeChoice.getSelectedIndex();
				
				saveSettings();
			}
			display(mainForm, true);
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
				
				fontSizeChoice = new ChoiceGroup("Размер шрифта", ChoiceGroup.POPUP, new String[] {
						"Мелкий",
						"Обычный",
						"Крупный"
				}, null);
				fontSizeChoice.setSelectedIndex(fontSize, true);
				f.append(fontSizeChoice);
				
				chapterSetsChoice = new ChoiceGroup("", ChoiceGroup.MULTIPLE, new String[] {
						"Показ во время парсинга",
						"Откл. форматирование",
						"Загр. иллюстрации",
						"Загр. обложки"
				}, null);
				chapterSetsChoice.setSelectedIndex(0, showChapterWhileParsing);
				chapterSetsChoice.setSelectedIndex(1, noFormat);
				chapterSetsChoice.setSelectedIndex(2, loadChapterImages);
				chapterSetsChoice.setSelectedIndex(3, loadCovers);
				f.append(chapterSetsChoice);
				
				proxyField = new TextField("URL прокси", proxyUrl, 200, TextField.NON_PREDICTIVE);
				f.append(proxyField);
				
				proxyChoice = new ChoiceGroup("", ChoiceGroup.MULTIPLE, new String[] {
						"Исп. прокси",
						"Онлайн масшт. изображений"
				}, null);
				proxyChoice.setSelectedIndex(0, useProxy);
				proxyChoice.setSelectedIndex(1, onlineResize);
				f.append(proxyChoice);
				
				coversChoice = new ChoiceGroup("Загрузка изображений", ChoiceGroup.POPUP, new String[] {
						"Авто", "1 поток", "Мультипоток", "Выкл."
				}, null);
				coversChoice.setSelectedIndex(thumbLoading, true);
				f.append(coversChoice);
			}
			display(settingsForm);
			return;
		}
		if (c == cancelCmd) {
			display(null, true);
			return;
		}
		if (c == okCmd && d instanceof TextBox) { // set brightness
			String s = ((TextBox) d).getString();
			try {
				int value = Integer.parseInt(s);
				if (value < -1 || value > 100) throw new Exception();
				setLight(brightness = value);
				saveSettings();
				display(null, true);
				return;
			} catch (Exception e) {
				c = brightnessCmd;
			}
		}
		if (c == brightnessCmd) {
			if (!hasNokiaUi) return;
			
			TextBox t = new TextBox("Яркость (0-100)", brightness != -1 ? Integer.toString(brightness) : "100", 3, TextField.NUMERIC);
			t.addCommand(okCmd);
			t.addCommand(cancelCmd);
			t.setCommandListener(this);
			display(t);
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

			chapterParams = s;
			
			Displayable f;
			if (useCanvas) {
				f = new ReadCanvas();
			} else {
				f = new Form(s);
			}
			f.addCommand(backCmd);
			if (hasNokiaUi) f.addCommand(brightnessCmd);
			f.setCommandListener(this);
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
						if (url.startsWith("/")) {
							url = "https://ranobelib.me".concat(url);
						}
						
						try {
							Image img;
							if (onlineResize) {
								img = getImage(proxyUrl(url + ";jpg;th=" + (getHeight() / 3)));
							} else {
								img = getImage(proxyUrl(url));
	
								int h = getHeight() / 3;
								int w = (int) (((float) h / img.getHeight()) * img.getWidth());
								img = resize(img, w, h);
							}
							
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
					
					if (loadCovers) {
						JSONObject cover = v.getObject("cover");
						String url = cover.getString("default", cover.getString("thumbnail"));
						scheduleThumb(item, url);
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
				try {
					JSONObject j = (JSONObject) api(sb.toString());
					
					s = new StringItem(null, j.getString("rus_name", j.getString("name")));
					s.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
					f.append(s);
					// TODO
				} catch (Exception ignored) {}
				
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
		case RUN_CHAPTER: { // load chapter
			Displayable f = chapterForm;
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

				sb.setLength(0);
				
				chapterAttachments = j.getArray("attachments", null);
				
				Object content = j.get("content");
				parse: {
					StringItem s;
					boolean noFormat = Ran.noFormat;
					if (content instanceof String) { // parse html
						String src = (String) content;
						int d = src.indexOf('<');
						int len = src.length();
						if (len == 0) break parse;
						int o = 0;
						int fstyle = Font.STYLE_PLAIN;
						int fsize = Font.SIZE_MEDIUM;
						
						if (gauge != null) gauge.setMaxValue(202);
						char[] chars = src.toCharArray();
						while (d != -1 || o < len) {
							if (o != d) {
								if (d == -1) d = len;
								char l = 0;
								for (int i = o; i < d; ++i) {
									char c = chars[i];
									if (c == '\r' || c == '\n' || c == '\t') { // whitespace
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
									if (c == ' ' && l == ' ') { // ignore repeated whitespace
										continue;
									}
									l = c;
									sb.append(c);
								}
								
								if (!noFormat && sb.length() != 0) {
									if (f instanceof ReadCanvas) {
										((ReadCanvas) f).append(sb.toString(), getFont(0, fstyle, fsize));
									} else {
										s = new StringItem(null, sb.toString());
										s.setFont(getFont(0, fstyle, fsize));
										((Form) f).append(s);
									}
									
									sb.setLength(0);
								}
								if (d == len) break;
							}
							int e = src.indexOf('>', d);
							if (noFormat) {
								if (chars[d + 1] == 'p'
										|| (chars[d + 1] == '/' && chars[d + 2] == 'p')
										|| (chars[d + 1] == 'b' && chars[d + 2] == 'r')) {
									sb.append('\n');
								}
							} else { // format by tags
								if (chars[d + 1] == '/') {
									if ((chars[d + 2] == 'b' && chars[d + 3] == '>')
											|| (chars[d + 2] == 's' && chars[d + 3] == 't')) {
										// </b> or </strong>
										fstyle &= ~Font.STYLE_BOLD;
									} else if (chars[d + 2] == 'h') { // </h
										fsize = Font.SIZE_MEDIUM;
										// </h1>
										if (chars[d + 3] == '1') fstyle &= ~Font.STYLE_BOLD;
									} else if ((chars[d + 2] == 'e' && chars[d + 3] == 'm')
										|| (chars[d + 2] == 'i' && chars[d + 3] == '>')) {
										// </em> or </i>
										fstyle &= ~Font.STYLE_ITALIC;
									} else if (chars[d + 2] == 's' && chars[d + 3] == 'm') {
										// </small>
										fsize = Font.SIZE_MEDIUM;
									} else if (chars[d + 2] == 's' && chars[d + 3] == 'u') {
										// </sub>
										fstyle &= ~Font.STYLE_UNDERLINED;
									} else if (chars[d + 2] == 'p') {
										// </p>
										sb.append('\n');
									}
								} else {
									if ((chars[d + 1] == 'b' && chars[d + 2] == '>')
											|| (chars[d + 1] == 's' && chars[d + 2] == 't')) {
										// <b> or <strong>
										fstyle |= Font.STYLE_BOLD;
									} else if (chars[d + 1] == 'h') {
										// <h
										fsize = Font.SIZE_LARGE;
										// <h1>
										if (chars[d + 2] == '1') fstyle |= Font.STYLE_BOLD;
									} else if ((chars[d + 1] == 'e' && chars[d + 2] == 'm')
											|| (chars[d + 1] == 'i' && chars[d + 2] == '>')) {
										// <em> or <i>
										fstyle |= Font.STYLE_ITALIC;
									} else if (chars[d + 1] == 's' && chars[d + 2] == 'm') {
										// <small>
										fsize = Font.SIZE_SMALL;
									} else if (chars[d + 1] == 's' && chars[d + 2] == 'u') {
										// <sub>
										fstyle |= Font.STYLE_UNDERLINED;
									} else if (chars[d + 1] == 'p'
											|| (chars[d + 1] == 'b' && chars[d + 2] == 'r')) {
										// <p> or <br>
										sb.append('\n');
									} else if (chars[d + 1] == 'i' && chars[d + 2] == 'm') {
										// <img>
										
										if (f instanceof ReadCanvas) {
											
										} else {
											ImageItem img = new ImageItem("Картинка", null, 0, null);
											if (loadChapterImages) {
												img.setLabel("");
												String url = src.substring(d + 4, e);
												int i;
												if ((i = url.indexOf("src=")) != -1) {
													url = url.substring(i + 4);
													if ((i = url.indexOf(' ')) != -1) {
														url = url.substring(0, i);
													}
													
													if (url.charAt(0) == '"')
														url = url.substring(1, url.length() - 1);
													
													img.setAltText(url);
													scheduleThumb(img, url);
												}
											}
											
											if (sb.length() != 0) {
												s = new StringItem(null, sb.toString());
												s.setFont(getFont(0, fstyle, fsize));
												((Form) f).append(s);
												
												sb.setLength(0);
											}
											
											((Form) f).append(img);
										}
									}
									
									// TODO: <li> ?
								}
							}
							d = src.indexOf('<', o = e + 1);
							if (d != -1 && gauge != null) {
								gauge.setValue((d*200)/len);
							}
						}
						chars = null;
					} else {
						// json
						String type = ((JSONObject) content).getString("type");
						if ("doc".equals(type)) {
							parseJsonContent(f, ((JSONObject) content).getArray("content"), gauge, noFormat ? sb : null);
						} else {
							// unknown
							if (f instanceof ReadCanvas) {
								((ReadCanvas) f).append(content.toString(), medPlainFont);
							} else {
								((Form) f).append(content.toString());
							}
						}
					}
					if (noFormat) {
						if (f instanceof ReadCanvas) {
							((ReadCanvas) f).append(content.toString(), medPlainFont);
						} else {
							s = new StringItem(null, sb.toString());
							s.setFont(getFont(0, Font.STYLE_PLAIN, Font.SIZE_MEDIUM));
							((Form) f).append(s);
							((Form) f).append("\n\n");
						}
						sb.setLength(0);
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
				if (useCanvas) repaint();
			} catch (Exception e) {
				e.printStackTrace();
				if (chapterForm == f)
					display(errorAlert(e.toString()), f);
			}
			break;
		}
		case RUN_REPAINT: {
			try {
				painterRunning = true;
				while (!exiting) {
					Displayable d;
					while (!((d = display.getCurrent()) instanceof ReadCanvas) || !d.isShown()) {
						synchronized (repaintLock) {
							repaintLock.wait();
						}
					}
					ReadCanvas r = (ReadCanvas) d;
					if (!r.scrolling) {
						repaint = false;
						_repaint(r);
						if (repaint) continue;
						synchronized (repaintLock) {
							repaintLock.wait(10000);
						}
						continue;
					}
					_repaint(r);
					// limit
					int i = 33;
					i -= repaintTime;
					if (i > 0) Thread.sleep(i);
				}
			} catch (Exception ignored) {
			} finally {
				painterRunning = false;
			}
			break;
		}
		}
		running = false;
	}
	
	private void _repaint(ReadCanvas canvas) {
		long time = System.currentTimeMillis();
		canvas.paint();
		repaintTime = (int) (System.currentTimeMillis() - time);
	}
	
	static void repaint() {
		if (!painterRunning) {
			painterRunning = true;
			repaint = true;
			midlet.start(RUN_REPAINT);
			return;
		}
		repaint = true;
		synchronized (repaintLock) {
			repaintLock.notify();
		}
	}

	private static void parseJsonContent(Displayable f, JSONArray content, Gauge gauge, StringBuffer sb) {
		// if sb is not null, turn off formatting
		
		int l = content.size();
		if (l == 0) return;
		if (gauge != null) gauge.setMaxValue(l);
		for (int i = 0; i < l; ++i) {
			JSONObject e = content.getObject(i);
			String type = e.getString("type");
			
			if ("paragraph".equals(type)) {
				if (sb != null) {
					sb.append('\n');
					if (e.has("content")) parseJsonContent(f, e.getArray("content"), null, sb);
					sb.append('\n');
				} else {
					if (f instanceof ReadCanvas) {
						((ReadCanvas) f).beginParagraph();
					} else {
						((Form) f).append("\n");
					}
					if (e.has("content")) parseJsonContent(f, e.getArray("content"), null, null);
					
//					Spacer s = new Spacer(8, smallPlainFont.getHeight());
//					s.setLayout(Item.LAYOUT_LEFT | Item.LAYOUT_NEWLINE_AFTER | Item.LAYOUT_NEWLINE_BEFORE);
//					f.append(s);

					if (f instanceof ReadCanvas) {
						((ReadCanvas) f).endParagraph();
					} else {
						((Form) f).append("\n");
					}
				}
				
			} else if ("text".equals(type)) {
				if (sb != null) {
					sb.append(e.getString("text"));
				} else {
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
					}
					if (f instanceof ReadCanvas) {
						((ReadCanvas) f).append(e.getString("text"), getFont(0, style, Font.SIZE_MEDIUM));
					} else {
						StringItem s = new StringItem(null, e.getString("text"));
						s.setFont(getFont(0, style, Font.SIZE_MEDIUM));
						((Form) f).append(s);
					}
				}
			} else if ("listItem".equals(type)) {
				// TODO
				if (e.has("content")) parseJsonContent(f, e.getArray("content"), null, sb);
			} else if (sb == null && "image".equals(type)) {
				if (!(f instanceof ReadCanvas)) {
					ImageItem img = new ImageItem("Картинка", null, 0, null);
					if (loadChapterImages && chapterAttachments != null) {
						JSONObject attrs = e.getObject("attrs");
						String id = attrs.getArray("images").getObject(0).getString("image");
						img.setLabel(attrs.getString("description", ""));
						img.setAltText(id);
						int attsSize = chapterAttachments.size();
						for (int n = 0; n < attsSize; ++n) {
							JSONObject attachment = chapterAttachments.getObject(n);
							if (!id.equals(attachment.getString("name")))
								continue;
							String url = attachment.getString("url");
							scheduleThumb(img, url);
							break;
						}
					}
					((Form) f).append(img);
				}
			} else if ("hardBreak".equals(type)) {
				if (sb != null) {
					sb.append('\n');
				} else {
					if (f instanceof ReadCanvas) {
						((ReadCanvas) f).append("\n", medPlainFont);
					} else {
						((Form) f).append("\n");
					}
				}
			}
			if (gauge != null) gauge.setValue(i);
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
	
	private static void scheduleThumb(ImageItem item, String url) {
		if (thumbLoading == 3 || item == null || url == null) return;
		synchronized (thumbLoadLock) {
			thumbsToLoad.addElement(new Object[] { url, item });
			thumbLoadLock.notifyAll();
		}
	}
	
	private void saveSettings() {
		try {
			RecordStore.deleteRecordStore(SETTINGS_RMS);
		} catch (Exception e) {}
		try {
			JSONObject j = new JSONObject();
			j.put("proxy", proxyUrl);
			j.put("useProxy", useProxy);
			j.put("onlineResize", onlineResize);
			j.put("thumbLoading", thumbLoading);
			j.put("showChapterWhileParsing", showChapterWhileParsing);
			j.put("noFormat", noFormat);
			j.put("loadChapterImages", loadChapterImages);
			j.put("loadCovers", loadCovers);
			j.put("fontSize", fontSize);
			j.put("brightness", brightness);
			
			byte[] b = j.toString().getBytes("UTF-8");
			RecordStore r = RecordStore.openRecordStore(SETTINGS_RMS, true);
			r.addRecord(b, 0, b.length);
			r.closeRecordStore();
		} catch (Exception e) {}
	}
	
	private static int getHeight() {
		return mainForm.getHeight();
	}
	
	private static void setLight(int value) {
		if (value == -1) return;
		try {
			DeviceControl.setLights(0, value);
		} catch (Throwable e) {}
	}
	
	static void display(Alert a, Displayable d) {
		if (d == null) {
			display.setCurrent(a);
			return;
		}
		display.setCurrent(a, d);
	}
	
	static void display(Displayable d) {
		display(d, false);
	}

	static void display(Displayable d, boolean back) {
		if (d instanceof Alert) {
			display.setCurrent((Alert) d, mainForm);
			return;
		}
		if (d == null) {
			d = chapterForm != null ? chapterForm : mainForm;
		}
		Displayable p = display.getCurrent();
		display.setCurrent(d);
		// TODO resume thumbnail loading
//		if (p == null || p == d) return;
//		if (!back) return;
//		if (coverLoading == 3 || p == mainForm) return;
//		if (d == listForm) {
//			try {
//				for (int i = 0, l = ((Form) p).size(); i < l; i++) {
//					Item item = ((Form) d).get(i);
//					if (!(item instanceof ImageItem) ||
//							(((ImageItem) item).getImage() != null))
//						continue;
//					scheduleThumb((ImageItem) item, ((ImageItem) item).getAltText());
//				}
//			} catch (Exception e) {}
//		}
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

	private static Font getFont(int face, int style, int size) {
		if (face == 0) {
			int setSize = fontSize;
			if (setSize == 0) {
				size = size == Font.SIZE_LARGE ? Font.SIZE_MEDIUM : Font.SIZE_SMALL;
			} else if (setSize == 2) {
				size = size == Font.SIZE_SMALL ? Font.SIZE_MEDIUM : Font.SIZE_LARGE;
			}
			
			if (size == Font.SIZE_SMALL) {
				if (style == Font.STYLE_BOLD) {
					return smallBoldFont;
				}
				if (style == Font.STYLE_ITALIC) {
					return smallItalicFont;
				}
				if (style == Font.STYLE_PLAIN) {
					return smallPlainFont;
				}
			}
			if (size == Font.SIZE_MEDIUM) {
				if (style == Font.STYLE_BOLD) {
					return medBoldFont;
				}
				if (style == Font.STYLE_ITALIC) {
					return medItalicFont;
				}
				if (style == (Font.STYLE_BOLD | Font.STYLE_ITALIC)) {
					return medItalicBoldFont;
				}
				if (style == Font.STYLE_PLAIN) {
					return medPlainFont;
				}
			}
			if (size == Font.SIZE_LARGE && style == Font.STYLE_PLAIN) {
				return largePlainFont;
			}
		}
		return Font.getFont(face, style, size);
	}
	
	private static Object api(String url) throws IOException {
		Object res;

		HttpConnection hc = null;
		InputStream in = null;
		try {
			hc = open(proxyUrl(SOCIAL_API_URL.concat(url)));
			hc.setRequestMethod("GET");
			int c;
			if ((c = hc.getResponseCode()) >= 400) {
				throw new IOException("HTTP ".concat(Integer.toString(c)));
			}
			res = JSONStream.getStream(in = openInputStream(hc)).nextValue();
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
		while ((j = in.read(buf, i, buf.length - i)) != -1) {
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
			return readBytes(in = openInputStream(hc), (int) hc.getLength(), 8*1024, 16*1024);
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
	
	private static InputStream openInputStream(HttpConnection hc) throws IOException {
		InputStream i = hc.openInputStream();
		String enc = hc.getHeaderField("Content-Encoding");
		if ("deflate".equalsIgnoreCase(enc))
			i = new InflaterInputStream(i, new Inflater(true));
		else if ("gzip".equalsIgnoreCase(enc))
			i = new GZIPInputStream(i);
		return i;
	}
	
	private static HttpConnection open(String url) throws IOException {
		HttpConnection hc = (HttpConnection) Connector.open(url);
		hc.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0");
		hc.setRequestProperty("Origin", "https://ranobelib.me");
		hc.setRequestProperty("Referer", "https://ranobelib.me");
		if (compress) hc.setRequestProperty("Accept-Encoding", "gzip, deflate");
		return hc;
	}
	
	private static String proxyUrl(String url) {
		System.out.println(url);
		if (url == null
				|| (!useProxy && (url.indexOf(";t") == -1 || !onlineResize))
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
		
		for (int offset = 0, i = 0; i < h2; i++) {
			org.getRGB( buffer, 0, w1, 0, i * 2, w1, 2); // get two lines from the original
			
			int o1 = 0, o2 = 1;
			int o3 = w1, o4 = w1 + 1;
			
			for (int j = 0; j < w2; j++) {
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
