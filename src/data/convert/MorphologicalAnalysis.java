package data.convert;
import java.util.ArrayList;

public class MorphologicalAnalysis {

	private ArrayList<ArrayList<String>> word;
	private ArrayList<ArrayList<String>> hinshi;
	private ArrayList<ArrayList<String>> detailhinshi;
	private ArrayList<ArrayList<String>> origin;
	private ArrayList<ArrayList<Integer>> position;
	private ArrayList<ArrayList<Integer>> kakarisaki;
	private ArrayList<ArrayList<String>> katuyo;
	private ArrayList<ArrayList<String>> yomigana;

	private ArrayList<String> sentence;
	private ArrayList<ArrayList<String>> attrib;

	/*品詞など詳細情報まで取得する際にはdetailにtrue
	 */
	public MorphologicalAnalysis(ArrayList<String> word,
			boolean detail) {
		ArrayList<String> hankakuword = new ArrayList<String>(word.size());
		for(String s : word) {
			hankakuword.add(this.zenkakuNumToHankaku(s));
		}
		this.word = new ArrayList<ArrayList<String>>(hankakuword.size());
		//文章データの形式をArrayList<ArrayList>>からArrayListに変える
		sentence = new ArrayList<String>(hankakuword);
		if (detail) {
			hinshi = new ArrayList<ArrayList<String>>(hankakuword.size());
			detailhinshi = new ArrayList<ArrayList<String>>(hankakuword.size());
			origin = new ArrayList<ArrayList<String>>(hankakuword.size());
			position = new ArrayList<ArrayList<Integer>>(hankakuword.size());
			kakarisaki = new ArrayList<ArrayList<Integer>>(hankakuword.size());
			katuyo = new ArrayList<ArrayList<String>>(hankakuword.size());
			yomigana = new ArrayList<ArrayList<String>>(hankakuword.size());

			executeCabochaT(hankakuword);
		} else {
			executeCabochaF(hankakuword);
		}

		//型番データの抽出
		this.word = new ArrayList<ArrayList<String>> (this.typeselect(this.word));
		//記号(?,。単独部分の前方結合)
		this.end_signal_marge();

	}

	/*詳細情報まで取得する際のcabocha実行関数：属性なし
	 */
	private void executeCabochaT(ArrayList<String> word) {
		//形態素解析
		Cabocha cab = new Cabocha();
		cab.getcabocharesult(word);
		this.word = new ArrayList<ArrayList<String>>(cab.words);
		this.hinshi = new ArrayList<ArrayList<String>>(cab.hinshis);
		this.detailhinshi = new ArrayList<ArrayList<String>>(cab.detailhinshis);
		this.origin = new ArrayList<ArrayList<String>>(cab.origins);
		this.position = new ArrayList<ArrayList<Integer>>(cab.positions);
		this.kakarisaki = new ArrayList<ArrayList<Integer>>(cab.kakarisakis);
		this.katuyo = new ArrayList<ArrayList<String>>(cab.katuyos);
		this.yomigana = new ArrayList<ArrayList<String>>(cab.yomiganas);

	}	
	/*
	 * 詳細情報までは取得しない際のcabocha実行関数：属性なし
	 */
	private void executeCabochaF(ArrayList<String> word) {
		ArrayList<String> tmp_chr = new ArrayList<String>();
		tmp_chr.add("-");
		Cabocha cab = new Cabocha();
		for (int i = 0; i < word.size(); i++) {
			if (word.get(i).equals("-")) {
				this.word.add(tmp_chr);
			} else {
				String str = word.get(i).replace("\"", "");
				cab.executeCabochaT(str);
				cab.executeCabochaF(word.get(i));
				this.word.add(cab.getword());
			}
		}
	}

	/*変換関数群*/
	//全角→半角変換
	private String zenkakuNumToHankaku(String s) {
		StringBuffer sb = new StringBuffer(s);
		for (int i = 0; i < sb.length(); i++) {
			char c = sb.charAt(i);
			if (c >= '０' && c <= '９') {
				sb.setCharAt(i, (char)(c - '０' + '0'));
				c = sb.charAt(i);
			}
			if (c >= 'ａ' && c <= 'ｚ') {
				sb.setCharAt(i, (char) (c - 'ａ' + 'a'));
				c = sb.charAt(i);
			}
			if (c >= 'Ａ' && c <= 'Ｚ') {
				sb.setCharAt(i, (char) (c - 'Ａ' + 'A'));
				c = sb.charAt(i);
			}
			if (c >= 'a' && c <= 'z') {
				sb.setCharAt(i, (char) (c - 'a' + 'A'));
				c = sb.charAt(i);
			}
			if (c == '？') {
				sb.setCharAt(i, (char) (c - '？' + '?'));
			}
			if (c == '！') {
				sb.setCharAt(i, (char) (c - '！' + '!'));
			}
			if (c == '−') {
				if (i != 0 && this.alphabetORfigure(sb.charAt(i - 1))) {
					sb.setCharAt(i, (char) (c - '−' + '-'));
				}
			}
			if (c == 'ー') {
				if (i != 0 && this.alphabetORfigure(sb.charAt(i - 1))) {
					sb.setCharAt(i, (char) (c - 'ー' + '-'));
				}
			}
		}
		return sb.toString();
	}
	//型番抽出
	private ArrayList<ArrayList<String>> typeselect(ArrayList<ArrayList<String>> word) {
		ArrayList<ArrayList<String>> selectedword = new ArrayList<ArrayList<String>>(word);
		for (int i = 0; i < selectedword.size(); i++) {
			for (int j = selectedword.get(i).size() - 1; j >= 0; j--) {
				if (detailhinshi.get(i).get(j).equals("型番") || this.typesignal(selectedword.get(i).get(j)) || this.alphabetORfigure(selectedword.get(i).get(j))) {
					if (j != 0) {
						if (detailhinshi.get(i).get(j - 1).equals("型番") || this.typesignal(selectedword.get(i).get(j - 1)) || this.alphabetORfigure(selectedword.get(i).get(j - 1))) {
							selectedword.get(i).set(j - 1, selectedword.get(i).get(j - 1) + selectedword.get(i).get(j)); selectedword.get(i).remove(j);

							//品詞・詳細品詞・原形・活用データの手直し
							hinshi.get(i).set(j - 1, "名詞"); hinshi.get(i).remove(j);
							detailhinshi.get(i).set(j - 1, "型番"); detailhinshi.get(i).remove(j);
							origin.get(i).set(j - 1, origin.get(i).get(j - 1) + origin.get(i).get(j)); origin.get(i).remove(j);
							katuyo.get(i).set(j - 1, katuyo.get(i).get(j - 1) + katuyo.get(i).get(j)); katuyo.get(i).remove(j);
							yomigana.get(i).set(j - 1,  yomigana.get(i).get(j - 1) + yomigana.get(i).get(j)); yomigana.get(i).remove(j);

							//位置・係り関係データの手直し 結合する前方の単語のpositionの値が一つだけだったら後ろがずれる
							if (position.get(i).indexOf(position.get(i).get(j - 1)) == position.get(i).lastIndexOf(position.get(i).get(j - 1))) {
								for (int k = j; k < position.get(i).size(); k++) {
									position.get(i).set(k, position.get(i).get(k) - 1);
								}
								for (int k = 0; k < kakarisaki.get(i).size(); k++) {
									if (kakarisaki.get(i).get(k) >= position.get(i).get(j - 1)) {
										kakarisaki.get(i).set(k, kakarisaki.get(i).get(k) - 1);
									}
								}
							}
							//結合先のデータを消去する
							position.get(i).remove(j - 1);
							kakarisaki.get(i).remove(j - 1);

						}
					}
				}
			}
			//チェック項目
			for (int j = 0; j < kakarisaki.get(i).size(); j++) {
				if (!kakarisaki.get(i).get(j).equals(-1)) {
					if (!position.get(i).contains(kakarisaki.get(i).get(j))) {
						System.out.println(i);
					}
				}
			}
		}
		return selectedword;
	}
	//ワードがアルファベット・数字判定
	private boolean alphabetORfigure (String s) {
		 StringBuffer sb = new StringBuffer(s);
		    for (int i = 0; i < sb.length(); i++) {
		      char c = sb.charAt(i);
		      if (c < 'ａ' || c > 'ｚ') {
		    	  if (c < 'Ａ' || c > 'Ｚ') {
		    		  if (c < 'a' || c > 'z') {
		    			  if (c < 'A' || c > 'Z') {
		    				  if (c < '0' || c > '9') {
		    					  return false;
		    				  }
		    		      }
				      }
			      }
		      }
			  if (c == '?' || c == '!' || c == '？' || c == '！') {
				  return false;
			  }
		  }
		return true;
	}
	private boolean alphabetORfigure (char c) {
		if (c < 'ａ' || c > 'ｚ') {
	    	  if (c < 'Ａ' || c > 'Ｚ') {
	    		  if (c < 'a' || c > 'z') {
	    			  if (c < 'A' || c > 'Z') {
	    				  if (c < '0' || c > '9') {
	    					  return false;
	    				  }
	    		      }
			      }
		      }
	      }
		  if (c == '?' || c == '!' || c == '？' || c == '！') {
			  return false;
		  }
		return true;
	}
	//型番に使われる記号判定
	private boolean typesignal(String s) {
		if (s.equals("-")) {
			return true;
		}
		if (s.equals("ー")) {
			return true;
		}
		if (s.equals("_")) {
			return true;
		}
		if (s.equals("#")) {
			return true;
		}
		return false;
	}

	/*文末節が"?""。"のみの場合に前の文末と併合する関数
	 */
	private void end_signal_marge() {
		for (int i = 0; i < word.size(); i++) {
			ArrayList<Integer> MINUS_POS = new ArrayList<Integer>(this.series_of_minus(kakarisaki.get(i)));
			//System.out.println(i);
			if (!MINUS_POS.isEmpty()) {
				for (int j : MINUS_POS) {
					if (word.get(i).get(j).equals("?") || word.get(i).get(j).equals("。")) {
						this.reformat_pos_ka(i, j);
					}
				}
			}
		}
	}
	//-1が連続しない位置を探す
	private ArrayList<Integer> series_of_minus(ArrayList<Integer> kakari) {
		ArrayList<Integer> TMP = new ArrayList<Integer>(10);
		if (kakari.indexOf(-1) != 0) {
			for (int i = 1; i < kakari.size() - 1; i++) {
				if (kakari.get(i).equals(-1)) {
					if (!kakari.get(i - 1).equals(-1) && !kakari.get(i + 1).equals(-1)) {
						TMP.add(i);
					}
				}
			}
			if (kakari.size() > 1 && !kakari.get(kakari.size() - 2).equals(-1)) {
				TMP.add(kakari.size() - 1);
			}
		}
		return TMP;
	}
	//positionとkakarisaki直す
	private void reformat_pos_ka(int i, int pos) {
		int CHANGE_POSITION_NUM = position.get(i).get(pos);
		int TO_POSITION_NUM = position.get(i).get(pos - 1);
		for (int j = pos; j < position.get(i).size(); j++) {
			position.get(i).set(j, position.get(i).get(j) - 1);
		}
		for (int j = 0; j < position.get(i).size(); j++) {
			if (!kakarisaki.get(i).get(j).equals(-1) && kakarisaki.get(i).get(j) >= CHANGE_POSITION_NUM) {
				kakarisaki.get(i).set(j, kakarisaki.get(i).get(j) - 1);
			}
			if (position.get(i).get(j).equals(TO_POSITION_NUM)) {
				kakarisaki.get(i).set(j, -1);
			}
		}
	}


	/*出力関数群*/
	public ArrayList<String> getSentence() {
		return sentence;
	}
	public ArrayList<ArrayList<String>> getmorphword() {
		return word;
	}
	public ArrayList<ArrayList<String>> getAttribute() {
		return attrib;
	}
	public ArrayList<ArrayList<String>> getmorphhinshi() {
		return hinshi;
	}
	public ArrayList<ArrayList<String>> getmorphdetailhinshi() {
		return detailhinshi;
	}
	public ArrayList<ArrayList<String>> getmorphorigin() {
		return origin;
	}
	public ArrayList<ArrayList<Integer>> getmorphposition() {
		return position;
	}
	public ArrayList<ArrayList<Integer>> getmorphkakarisaki() {
		return kakarisaki;
	}
	public ArrayList<ArrayList<String>> getmorphkatuyo() {
		return katuyo;
	}
	public ArrayList<ArrayList<String>> getmorphyomigana() {
		return yomigana;
	}
	public ArrayList<String> getmorphword(int i) {
		return word.get(i);
	}
	public ArrayList<String> getmorphhinshi(int i) {
		return hinshi.get(i);
	}
	public ArrayList<String> getmorphdetailhinshi(int i) {
		return detailhinshi.get(i);
	}
	public ArrayList<String> getmorphorigin(int i) {
		return origin.get(i);
	}
	public ArrayList<Integer> getmorphposition(int i) {
		return position.get(i);
	}
	public ArrayList<Integer> getmorphkakarisaki(int i) {
		return kakarisaki.get(i);
	}
	public ArrayList<String> getmorphkatuyo(int i) {
		return katuyo.get(i);
	}
	public ArrayList<String> getmorphyomigana(int i) {
		return yomigana.get(i);
	}
}
