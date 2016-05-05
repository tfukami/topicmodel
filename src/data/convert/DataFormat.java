package data.convert;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class DataFormat {
	/*
	 入力データ*/
	private MorphologicalAnalysis ma;
	private ArrayList<String> sentence;

	/*
	 出力データ*/
	public ArrayList<ArrayList<String>> origin;
	public ArrayList<ArrayList<String>> hinshi;
	public ArrayList<ArrayList<String>> dhinshi;

	/*
	内部計算用データ */
	private ArrayList<String> exclude_word;

	public DataFormat(ArrayList<String> sentence) {
		this.sentence = new ArrayList<String>(sentence);
		ma = new MorphologicalAnalysis(sentence, true);
		origin = new ArrayList<ArrayList<String>>(ma.getmorphorigin());
		hinshi = new ArrayList<ArrayList<String>>(ma.getmorphhinshi());
		dhinshi = new ArrayList<ArrayList<String>>(ma.getmorphdetailhinshi());
		this.set_exclude_word();
	}

	private void set_exclude_word() {
		String url = "exclude_word.csv";
		exclude_word = new ArrayList<String>();
		try {
			BufferedReader br = 
					new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream(url),"UTF-8"));
			String line;
			while ((line = br.readLine()) != null) {
				String[] w = line.split(",");
				for (String s : w) {
					exclude_word.add(s);
				}
			}
			br.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	public void execute_Norm_Anno_topic(int Cutting_Num) {
		for (int i = 0; i < sentence.size(); i++) {
			ArrayList<String> o = new ArrayList<String>(origin.get(i).size());
			ArrayList<String> h = new ArrayList<String>(hinshi.get(i).size());
			ArrayList<String> dh = new ArrayList<String>(dhinshi.get(i).size());
			for (int j = 0; j < origin.get(i).size(); j++) {
				if (hinshi.get(i).get(j).equals("名詞")) {
					if (!exclude_word.contains(origin.get(i).get(j))) {
						if (!dhinshi.get(i).get(j).equals("代名詞") && !dhinshi.get(i).get(j).equals("非自立")) {
							o.add(origin.get(i).get(j)); h.add(hinshi.get(i).get(j)); dh.add(dhinshi.get(i).get(j));
						}
					}
				}
			}
			origin.add(o);
			hinshi.add(h);
			dhinshi.add(dh);
		}
		if (Cutting_Num > 0) {
			this.Cutter(Cutting_Num);
		}
	}
	
	private void Cutter(int Cutting_Num) {
		AllWordVector awv = new AllWordVector();
		awv.CollectWord_H(origin, hinshi, dhinshi);
		ArrayList<String> delete_word = new ArrayList<String>(awv.CollectWrd.size() / 2);
		for (int i = 0; i < awv.CollectWrd.size(); i++) {
			if (awv.CollectCNT.get(i) <= Cutting_Num) {
				delete_word.add(awv.CollectWrd.get(i) + "_" + awv.CollectHnsh.get(i) + "_" + awv.CollectDHnsh.get(i));
			}
		}
		int under_cut_word = awv.CollectCNT.size() - delete_word.size();
		System.out.println("--Underwordcut:_" + Cutting_Num + " vec_size:" + awv.CollectCNT.size() + " -> " + under_cut_word);
		for (int i = 0; i < origin.size(); i++) {
			for (int j = origin.get(i).size() - 1; j >= 0; j--) {
				String word = origin.get(i).get(j) + "_" + hinshi.get(i).get(j) + "_" + dhinshi.get(i).get(j);
				if (delete_word.contains(word)) {
					origin.get(i).remove(j);
					hinshi.get(i).remove(j);
					dhinshi.get(i).remove(j);
				}
			}
			if (origin.get(i).size() == 0) {
				System.out.println("【ID:" + i + "】data all removed -> -");
				origin.get(i).add("-");
				hinshi.get(i).add("-");
				dhinshi.get(i).add("-");
			}
		}
	}

}

