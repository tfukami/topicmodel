package topicmodel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;

import org.apache.commons.math3.special.Gamma;

import data.convert.AllWordVector;
import data.convert.DataFormat;

public class TopicModel implements Serializable {
	private static final long serialVersionUID = 6255752248513019027L;

	/*
	 入力データ*/
	private ArrayList<String> sentence;
	private ArrayList<ArrayList<String>> origin;
	private ArrayList<ArrayList<String>> hinshi;
	private ArrayList<ArrayList<String>> dhinshi;
	private ArrayList<String> index  = null;
	public double topicNum;

	/*
	 出力データ*/
	ArrayList<ArrayList<Integer>> Zdn;//ワードが所属するtopicNumber
	public ArrayList<ArrayList<String>> keyword;//{topic,頻出順に並んだワード}
	public ArrayList<ArrayList<Double>> wordcnt;//{topic,↑に対応した出現数}
	ArrayList<ArrayList<Double>> thetadk;//θdk

	/*
	 内部計算用データ:Listの順序がtopicに対応*/
	public HashMap<String, ArrayList<Double>> Ndk;//{sentence,[topic,数]
	public ArrayList<Double> Nk;//{topic順}
	public AllWordVector awv;//ワード種類
	public ArrayList<ArrayList<Double>> Nkv;//各topicに含まれる数：awvに対応[ワード種類,topic]
	public double V;//単語種類数
	private int OUTPUT_NUM;//出力するワード数
	public HashMap<Integer, Integer> sortIndex;//ソート後の対応{key:ソート後のtopicid}{value:ソート前のtopic}

	/*
	 hyperparameter*/
	private double alpha = 0.1;//固定alpha alphakの初期値
	private ArrayList<Double> alphak;
	private double beta = 0.1;

	public TopicModel(ArrayList<String> sentence,
			ArrayList<ArrayList<String>> origin,
			ArrayList<ArrayList<String>> hinshi,
			ArrayList<ArrayList<String>> dhinshi,
			int topicNum, int OUTPUT_NUM) {
		this.sentence = new ArrayList<String>(sentence);
		this.topicNum = topicNum;
		this.origin = new ArrayList<ArrayList<String>>(origin);
		this.hinshi = new ArrayList<ArrayList<String>>(hinshi);
		this.dhinshi = new ArrayList<ArrayList<String>>(dhinshi);
		V = this.getWordKind();
		this.OUTPUT_NUM = OUTPUT_NUM;
	}
	public void setIndex(ArrayList<String> index) {
		this.index = new ArrayList<String>(index);
	}

	/*
	 RUN_TIME:反復回数*/
	public boolean execute(int RUN_TIME) {
		//初期化
		this.initialize();
		//while(条件満たすまで
		int counter = 0;
		while (counter < RUN_TIME) {
			counter++;

			//N,Zの更新 サンプリング
			this.renewal_Zdn(true);

			//hyperparameter更新
			alphak = new ArrayList<Double>(this.renew_alphak());
			if (alphak.isEmpty()) return false;
			beta = this.renew_beta();

			int r = counter * 100 / RUN_TIME;
			if(((double)counter * 100. / (double)RUN_TIME) % 10. == 0.)
				System.out.println("【" + r + "%終了】-" + new Date().toString());
		}
		Date d = new Date();
		System.out.println("--パラメータ更新終了：" + d.toString());
		this.collect_result();
		this.thetadk();

		return true;
	}
	
	private void initialize() {
		//Zdn,Ndk初期化：全て-1,0
		Zdn = new ArrayList<ArrayList<Integer>>();
		Ndk = new HashMap<String, ArrayList<Double>>();
		for (int i = 0; i < sentence.size(); i++) {
			ArrayList<Integer> tmp = new ArrayList<Integer>();
			for (int j = 0; j < origin.get(i).size(); j++) {
				tmp.add(-1);
			}
			Zdn.add(tmp);

			ArrayList<Double> tmpk = new ArrayList<Double>((int)topicNum);
			for (int j = 0; j < topicNum; j++) {
				tmpk.add(0.);
			}
			Ndk.put(sentence.get(i), tmpk);
		}

		//Nkv初期化：
		Nkv = new ArrayList<ArrayList<Double>>();
		for (int i = 0; i < awv.CollectWrd.size(); i++) {
			ArrayList<Double> tmp = new ArrayList<Double>((int)topicNum);
			for (int j = 0; j < topicNum; j++) {
				tmp.add(0.);
			}
			Nkv.add(tmp);
		}

		//Nk初期化
		Nk = new ArrayList<Double>();
		for (int i = 0; i < topicNum; i++) {
			Nk.add(0.);
		}

		//alphak初期化
		alphak = new ArrayList<Double>((int)topicNum);
		for (int i = 0; i < topicNum; i++) {
			alphak.add(alpha);
		}
	}
	private void renewal_Zdn(boolean val_alpha) {
		Random rd;
		for (int i = 0; i < sentence.size(); i++) {
			for (int j = 0; j < origin.get(i).size(); j++) {
				int zdn = Zdn.get(i).get(j);
				String sntnc = sentence.get(i);
				int nkv = -1;//Nkvの位置
				for (int k = 0; k < awv.CollectWrd.size(); k++) {
					if (awv.CollectWrd.get(k).equals(origin.get(i).get(j)) &&
							awv.CollectHnsh.get(k).equals(hinshi.get(i).get(j)) &&
							awv.CollectDHnsh.get(k).equals(dhinshi.get(i).get(j))) {
						nkv = k;
						break;
					}
				}

				//N(カウント)の処理
				if (zdn != -1) {
					Ndk.get(sntnc).set(zdn, Ndk.get(sntnc).get(zdn) - 1.);
					Nk.set(zdn, Nk.get(zdn) - 1.);
					Nkv.get(nkv).set(zdn, Nkv.get(nkv).get(zdn) - 1.);
				}

				//サンプリング
				ArrayList<Double> tmp_zdn = new ArrayList<Double>((int)topicNum);
				ArrayList<Double> tmp_zdn_base = new ArrayList<Double>((int)topicNum);
				double tmp_zdn_dinomina = 0.;
				for (int k = 0; k < topicNum; k++) {
					double calc_zdn = 0;
					if (val_alpha) {
						calc_zdn = (Ndk.get(sntnc).get(k) + alphak.get(k)) * (Nkv.get(nkv).get(k) + beta) / (Nk.get(k) + beta * V);
					} else {
						calc_zdn = (Ndk.get(sntnc).get(k) + alpha) * (Nkv.get(nkv).get(k) + beta) / (Nk.get(k) + beta * V);
					}
					tmp_zdn.add(calc_zdn);
					tmp_zdn_base.add(calc_zdn);
					tmp_zdn_dinomina += tmp_zdn.get(k);
				}
				Collections.sort(tmp_zdn_base);
				Collections.reverse(tmp_zdn_base);

				//RAND()発生：確率の大きいところに入りやすい仕様
				 rd = new Random();
				 int k = rd.nextInt((int)topicNum);
				 double tmp_zdn_nume = 0.;
				 for (int l = 0; l < tmp_zdn_base.size(); l++) {
					 tmp_zdn_nume += tmp_zdn_base.get(l);
					 if ((double)k / (double)topicNum < tmp_zdn_nume / tmp_zdn_dinomina) {
						 int pos = tmp_zdn.indexOf(tmp_zdn_base.get(l));
						 Zdn.get(i).set(j, pos);
						 break;
					 }
				 }

				 //N(カウント)の更新
				 zdn = Zdn.get(i).get(j);
				 Ndk.get(sntnc).set(zdn, Ndk.get(sntnc).get(zdn) + 1.);
				 Nk.set(zdn, Nk.get(zdn) + 1.);
				 Nkv.get(nkv).set(zdn, Nkv.get(nkv).get(zdn) + 1.);
			}
		}
	}
	private ArrayList<Double> renew_alphak() {
		double sigalphak = 0.;//共通項Σalphak
		double new_alpha_nume = 0.;
		double new_alpha_denomina = 0.;
		for (double d : alphak) {
			sigalphak += d;
		}

		ArrayList<Double> tmp_alpha = new ArrayList<Double>((int)topicNum);
		double digammasigalpha = 0;
		try{
			digammasigalpha = Gamma.digamma(sigalphak);
		} catch(java.lang.Error e) {
			System.out.println();
		}
		
		for (int t = 0; t < topicNum; t++) {
			for (int i = 0; i < sentence.size(); i++) {
				try{
					new_alpha_nume += Gamma.digamma(Ndk.get(sentence.get(i)).get(t) + alphak.get(t)) 
							- Gamma.digamma(alphak.get(t));
					new_alpha_denomina += Gamma.digamma(origin.get(i).size() + sigalphak) 
							- digammasigalpha;
				} catch(java.lang.Error e) {
					System.out.println("alpha error");
				}
			}
			double new_alpha = alphak.get(t) * new_alpha_nume / new_alpha_denomina;
			
			//topicに当てはまるワードが0の場合初期値に戻す
			if (Double.isNaN(new_alpha) || new_alpha_nume == 0.) {
				new_alpha = alpha;
			}
			
			tmp_alpha.add(new_alpha);
		}

		return tmp_alpha;
	}
	private double renew_beta() {
		double new_beta_nume = 0.;
		double new_beta_denomina = 0.;

		double gamma_beta = 0.;
		double Vgamma_beta = 0.;
		try {
			gamma_beta = Gamma.digamma(beta);
			Vgamma_beta = Gamma.digamma(V * beta);
		} catch(java.lang.Error e) {
			System.out.println("beta error");
		}
		for (int i = 0; i < topicNum; i++) {
			try{
				for (int j = 0; j < V; j++) {
					new_beta_nume += Gamma.digamma(Nkv.get(j).get(i) + beta) - gamma_beta;
				}
				new_beta_denomina += V * (Gamma.digamma(Nk.get(i) + beta * V) - Vgamma_beta);
			} catch(java.lang.Error e) {
				System.out.println("beta error");
			}
		}
		
		double new_beta = beta * new_beta_nume / new_beta_denomina;
		if (new_beta <= 0.|| Double.isNaN(new_beta)) {
			System.out.println("beta negative");
		}

		return new_beta;
	}
	
	private double getWordKind() {
		awv = new AllWordVector();
		awv.CollectWord_H(origin, hinshi, dhinshi);
		return awv.CollectWrd.size();
	}
	private void collect_result() {
		keyword = new ArrayList<ArrayList<String>>((int)topicNum);
		wordcnt = new ArrayList<ArrayList<Double>>((int)topicNum);
		ArrayList<ArrayList<Double>> base_cnt = new ArrayList<ArrayList<Double>>((int)topicNum);
		for (int i = 0; i < topicNum; i++) {
			ArrayList<Double> tmp = new ArrayList<Double>((int)V);
			ArrayList<Double> base_tmp = new ArrayList<Double>((int)V);
			for (int j = 0; j < V; j++) {
				tmp.add(0.);
				base_tmp.add(0.);
			}
			base_cnt.add(tmp);
			wordcnt.add(base_tmp);
		}
		for (int i = 0; i < Nkv.size(); i++) {
			for (int j = 0; j < Nkv.get(i).size(); j++) {
				base_cnt.get(j).set(i, base_cnt.get(j).get(i) + Nkv.get(i).get(j));
				wordcnt.get(j).set(i, wordcnt.get(j).get(i) + Nkv.get(i).get(j));
			}
		}

		for (int i = 0; i < wordcnt.size(); i++) {
			Collections.sort(wordcnt.get(i));
			Collections.reverse(wordcnt.get(i));
		}
		//ワード数上位に絞り込み
		if (OUTPUT_NUM > -1) {
			for (int i = 0 ; i < wordcnt.size(); i++) {
				for (int j = wordcnt.get(i).size() - 1; j >= OUTPUT_NUM; j--) {
					wordcnt.get(i).remove(j);
				}
			}
		}

		for (int i = 0; i < base_cnt.size(); i++) {
			ArrayList<String> tmp = new ArrayList<String>(awv.CollectWrd.size());
			for (int j = 0; j < wordcnt.get(i).size(); j++) {
				for (int k = 0; k < base_cnt.get(i).size(); k++) {
					if (wordcnt.get(i).get(j).equals(base_cnt.get(i).get(k))) {
						if (tmp.isEmpty() || !tmp.contains(awv.CollectWrd.get(k))) {
							tmp.add(awv.CollectWrd.get(k));
							break;
						}
					}
				}
			}
			keyword.add(tmp);
		}
	}

	public void output(String url) {
		File outputFile = new File(url);
		try{
			FileOutputStream fos = new FileOutputStream(outputFile);
			OutputStreamWriter osw = new OutputStreamWriter(fos);
			PrintWriter pw = new PrintWriter(osw);

			for (int i = 1; i <= topicNum; i++) {
				pw.print("Topic" + i + ",出現数,");
			}pw.println("");

			if (sortIndex == null) this.sort();
			for (int i  =0; i < OUTPUT_NUM; i++) {
				for (int j = 0; j < keyword.size(); j++) {
					if (keyword.get(j).size() > i && wordcnt.get(j).get(i) > 0) {
						pw.print(keyword.get(j).get(i) + "," + wordcnt.get(j).get(i) + ",");
					} else {
						pw.print(",,");
					}
				}
				pw.println("");
			}
			pw.println();
			for (int i = 0; i < keyword.size(); i++) {
				pw.println(keyword.get(i).get(0) + "," + wordcnt.get(i).get(0));
			}

			pw.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	private void sort() {
		ArrayList<ArrayList<String>> tmp_k = new ArrayList<ArrayList<String>>(keyword.size());
		ArrayList<ArrayList<Double>> tmp_c = new ArrayList<ArrayList<Double>>(wordcnt.size());
		for (int i = 0; i < keyword.size(); i++) {
			tmp_k.add(keyword.get(i));
			tmp_c.add(wordcnt.get(i));
		}
		sortIndex = new HashMap<Integer, Integer>((int)topicNum);

		ArrayList<Double> order = new ArrayList<Double>(wordcnt.size());
		for (int i = 0; i < wordcnt.size(); i++) {
			order.add(wordcnt.get(i).get(0));
		}

		Collections.sort(order);
		Collections.reverse(order);

		ArrayList<ArrayList<String>> sorted_k = new ArrayList<ArrayList<String>>(keyword.size());
		ArrayList<ArrayList<Double>> sorted_c = new ArrayList<ArrayList<Double>>(wordcnt.size());
		for (int i = 0; i < order.size(); i++) {
			/*for (int j = tmp_c.size() - 1; j >= 0; j--) {
				if (order.get(i) == tmp_c.get(j).get(0)) {
					sorted_k.add(tmp_k.get(j));
					sorted_c.add(tmp_c.get(j));
					tmp_k.remove(j);
					tmp_c.remove(j);
					sortIndex.put(i, j);
				}
			}*/
			ArrayList<Integer> memo = new ArrayList<Integer>((int)topicNum);
			for (int j = 0; j < order.size(); j++) {
				if (order.get(i) == tmp_c.get(j).get(0) && !memo.contains(j)) {
					memo.add(j);
					sorted_k.add(tmp_k.get(j));
					sorted_c.add(tmp_c.get(j));
					sortIndex.put(i, j);
				}
			}
		}

		keyword = new ArrayList<ArrayList<String>>(sorted_k);
		wordcnt = new ArrayList<ArrayList<Double>>(sorted_c);
	}

	//各センテンスのトピック依存確率
	private void thetadk() {
		thetadk = new ArrayList<ArrayList<Double>>(sentence.size());
		for (int i = 0; i < sentence.size(); i++) {
			ArrayList<Double> tmp = new ArrayList<Double>((int)topicNum);
			for (int j = 0; j < topicNum; j++) {
				tmp.add((Ndk.get(sentence.get(i)).get(j) + alphak.get(j)) / (origin.get(i).size() + alphak.get(j) * topicNum));
			}
			thetadk.add(tmp);
		}
	}
	//各トピック所属センテンス
	public HashMap<String, HashMap<ArrayList<String>, ArrayList<Double>>> topic_contents(int contentsnum) {
		int C_NUM = contentsnum;
		if (thetadk == null) return null;
		if (index == null) {
			index = new ArrayList<String>(thetadk.size());
			for (int i = 0; i < thetadk.size(); i++) index.add(String.valueOf(i));
		}
		if (thetadk.size() < contentsnum) C_NUM = thetadk.size();
		HashMap<String, HashMap<ArrayList<String>, ArrayList<Double>>> contents =
				new HashMap<String, HashMap<ArrayList<String>, ArrayList<Double>>>((int)topicNum);
		for (int i = 0; i < topicNum; i++) {
			ArrayList<Double> tmp = new ArrayList<Double>(thetadk.size());
			for (int j = 0; j < thetadk.size(); j++) {
				tmp.add(thetadk.get(j).get(i));
			}
			Collections.sort(tmp);Collections.reverse(tmp);
			for (int j = tmp.size() - 1; j >= C_NUM; j--) tmp.remove(j);

			ArrayList<String> tmpc = new ArrayList<String>(C_NUM);
			ArrayList<Integer> memo = new ArrayList<Integer>(C_NUM);
			for (int j = 0; j < C_NUM; j++) {
				for (int s = 0; s < thetadk.size(); s++) {
					if (tmp.get(j) == thetadk.get(s).get(i) && !memo.contains(s)) {
						tmpc.add(index.get(s));
						memo.add(s);
						break;
					} else if (tmp.get(j) == thetadk.get(s).get(i)) {
						System.out.println();
					}
					if(s == thetadk.size() - 1) {
						System.out.println();
					}
				}
			}
			HashMap<ArrayList<String>, ArrayList<Double>> tmph = new HashMap<ArrayList<String>, ArrayList<Double>>(1);
			tmph.put(tmpc, tmp);
			contents.put(String.valueOf(i), tmph);
		}
		return contents;
	}
	//各トピック所属センテンス出力
	public void outputcontents(int contentsnum, String filepath) {
		HashMap<String, HashMap<ArrayList<String>, ArrayList<Double>>> contents =
				new HashMap<String, HashMap<ArrayList<String>, ArrayList<Double>>>(this.topic_contents(contentsnum));
		File of = new File(filepath);
		try {
			FileOutputStream fos = new FileOutputStream(of);
			OutputStreamWriter osw = new OutputStreamWriter(fos);
			PrintWriter pw = new PrintWriter(osw);

			for (int i = 1; i < topicNum; i++) pw.print("Topic" + i + ",確率,");
			pw.println("Topic" + topicNum + ",確率");

			int sz = -1;
			for (int i = 0; i < contentsnum; i++) {
				if (i == sz) break;
				for (int j = 0; j < topicNum - 1; j++) {
					for (ArrayList<String> c : contents.get(String.valueOf(sortIndex.get(j))).keySet()) {
						if (c.size() < i - 1) {
							pw.print(",,");
						} else {
							pw.print(c.get(i) + ","+ contents.get(String.valueOf(sortIndex.get(j))).get(c).get(i) + ",");
						}
						sz = c.size();
					}
				}
				for (ArrayList<String> c : contents.get(String.valueOf(sortIndex.get((int)topicNum - 1))).keySet()) {
					if (c.size() < i - 1) {
						pw.print(",,");
					} else {
						pw.println(c.get(i) + "," + contents.get(String.valueOf(sortIndex.get((int)topicNum - 1))).get(c).get(i));
					}
				}
			}

			pw.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	//classSave
	public void save(String outputfile) throws Exception {
		if (sortIndex == null) this.sort();
		FileOutputStream outFile = new FileOutputStream(outputfile);
		ObjectOutputStream oos = new ObjectOutputStream(outFile);
		oos.writeObject(this);
		oos.close();
	}

	//新規データ推定_LDAiteration infer
	public HashMap<Integer, Double> infer(String sentence) {
		Random rd = new Random();
		ArrayList<String> s = new ArrayList<String>(1); s.add(sentence);
		DataFormat df = new DataFormat(s);
		df.execute_Norm_Anno_topic(0);

		ArrayList<Integer> nkv = new ArrayList<Integer>(df.origin.get(0).size());//Nkvの位置
		HashMap<Integer, Integer> ndk = new HashMap<Integer, Integer>((int)topicNum);
		for (int i = 0; i < (int)topicNum; i++) ndk.put(i, 0);
		ArrayList<Integer> zn = new ArrayList<Integer>(df.origin.get(0).size());
		for (int i = 0; i < df.origin.get(0).size(); i++) {
			for (int j = 0; j < awv.CollectWrd.size(); j++) {
				if (awv.CollectWrd.get(j).equals(df.origin.get(0).get(i)) &&
						awv.CollectHnsh.get(j).equals(df.hinshi.get(0).get(i)) &&
						awv.CollectDHnsh.get(j).equals(df.dhinshi.get(0).get(i))) {
					nkv.add(j);
					int tp = rd.nextInt((int)topicNum);
					zn.add(tp);
					if (ndk.containsKey(tp)) {
						ndk.put(tp, ndk.get(tp) + 1);
					}
					break;
				}
			}
		}

		int run_time = 1000;
		for (int i = 0; i < run_time; i++) {
			for (int j = 0; j < zn.size(); j++) {
				int zdn = zn.get(j);

				//N(カウント)の処理
				ndk.put(zdn, ndk.get(zdn) - 1);

				//サンプリング
				ArrayList<Double> tmp_zdn = new ArrayList<Double>((int)topicNum);
				ArrayList<Double> tmp_zdn_base = new ArrayList<Double>((int)topicNum);
				double tmp_zdn_dinomina = 0.;
				for (int k = 0; k < topicNum; k++) {
					double calc_zdn = (ndk.get(k) + alphak.get(k)) * (Nkv.get(nkv.get(j)).get(k) + beta) / (Nk.get(k) + beta * V);
					tmp_zdn.add(calc_zdn);
					tmp_zdn_base.add(calc_zdn);
					tmp_zdn_dinomina += tmp_zdn.get(k);
				}
				Collections.sort(tmp_zdn_base);
				Collections.reverse(tmp_zdn_base);

				//RAND()発生：確率の大きいところに入りやすい仕様
				int k = rd.nextInt((int)topicNum);
				double tmp_zdn_nume = 0.;
				for (int l = 0; l < tmp_zdn_base.size(); l++) {
					tmp_zdn_nume += tmp_zdn_base.get(l);
					if ((double)k / (double)topicNum < tmp_zdn_nume / tmp_zdn_dinomina) {
						int pos = tmp_zdn.indexOf(tmp_zdn_base.get(l));
						zn.set(j, pos);
						break;
					}
				}

				//N(カウント)の更新
				zdn = zn.get(j);
				ndk.put(zdn, ndk.get(zdn) + 1);
			}
		}

		HashMap<Integer, Double> tmp = new HashMap<Integer, Double>((int)topicNum);
		if (zn.size() == 0) {
			for (int j = 0; j < topicNum; j++) tmp.put(j, 0.);
		} else {
			for (int j = 0; j < topicNum; j++) {
				if (sortIndex != null) {
					tmp.put(j, (ndk.get(sortIndex.get(j)) + alphak.get(sortIndex.get(j))) / (zn.size() + alphak.get(sortIndex.get(j)) * topicNum));
				} else {
					tmp.put(j, (ndk.get(j) + alphak.get(j)) / (zn.size() + alphak.get(j) * topicNum));
				}
			}
		}

		return tmp;
	}
	
	//評価perplexity: 戻り値 {infinite発生(0,1),perplexity}
	public Double[] perplexity(ArrayList<ArrayList<String>> origin,
			ArrayList<ArrayList<String>> hinshi,
			ArrayList<ArrayList<String>> dhinshi,
			int RUN_TIME) {
		Double[] result = {0., 0.};//戻り値
		double logp = 0.;//Σlogp(w|M)
		double Ndtest = 0.;//ΣNdtest
		ArrayList<Integer> t_Nk = new ArrayList<Integer>((int)topicNum);//テストデータのNk
		for (int i = 0; i < (int)topicNum; i++) t_Nk.add(0);
		ArrayList<ArrayList<Double>> t_Nkv =
				new ArrayList<ArrayList<Double>>(awv.CollectWrd.size());//各topicに含まれる数：awvに対応[ワード種類,topic]
		for (int i = 0; i < awv.CollectWrd.size(); i++) {
			ArrayList<Double> t_tmp = new ArrayList<Double>((int)topicNum);
			for (int j = 0; j < (int)topicNum; j++) t_tmp.add(0.);
			t_Nkv.add(t_tmp);
		}
		ArrayList<ArrayList<Integer>> t_zn = new ArrayList<ArrayList<Integer>>(origin.size());//znの保存
		ArrayList<ArrayList<Integer>> t_nkv = new ArrayList<ArrayList<Integer>>(origin.size());//nkvの保存
		ArrayList<HashMap<Integer, Integer>> t_ndk = new ArrayList<HashMap<Integer, Integer>>(origin.size());//ndkの保存
		Random rd = new Random();

		ArrayList<Integer> memo_nkv = new ArrayList<Integer>((int)V);//テストデータの単語種類memory
		for (int s = 0; s < origin.size(); s++) {
			ArrayList<Integer> nkv = new ArrayList<Integer>(origin.get(s).size());//Nkvの位置
			HashMap<Integer, Integer> ndk = new HashMap<Integer, Integer>((int)topicNum);
			for (int i = 0; i < (int)topicNum; i++) ndk.put(i, 0);
			ArrayList<Integer> zn = new ArrayList<Integer>(origin.get(s).size());
			for (int i = 0; i < origin.get(s).size(); i++) {
				for (int j = 0; j < awv.CollectWrd.size(); j++) {
					if (awv.CollectWrd.get(j).equals(origin.get(s).get(i)) &&
							awv.CollectHnsh.get(j).equals(hinshi.get(s).get(i)) &&
							awv.CollectDHnsh.get(j).equals(dhinshi.get(s).get(i))) {
						nkv.add(j);
						if (!memo_nkv.contains(j)) memo_nkv.add(j);
						int tp = rd.nextInt((int)topicNum);
						zn.add(tp);
						if (ndk.containsKey(tp)) {
							ndk.put(tp, ndk.get(tp) + 1);
						}
						Ndtest++;//wordcount
						break;
					}
				}
			}

			for (int i = 0; i < RUN_TIME; i++) {
				for (int j = 0; j < zn.size(); j++) {
					int zdn = zn.get(j);

					//N(カウント)の処理
					ndk.put(zdn, ndk.get(zdn) - 1);

					//サンプリング
					ArrayList<Double> tmp_zdn = new ArrayList<Double>((int)topicNum);
					ArrayList<Double> tmp_zdn_base = new ArrayList<Double>((int)topicNum);
					double tmp_zdn_dinomina = 0.;
					for (int k = 0; k < topicNum; k++) {
						double calc_zdn = (ndk.get(k) + alphak.get(k)) * (Nkv.get(nkv.get(j)).get(k) + beta) / (Nk.get(k) + beta * V);
						tmp_zdn.add(calc_zdn);
						tmp_zdn_base.add(calc_zdn);
						tmp_zdn_dinomina += tmp_zdn.get(k);
					}
					Collections.sort(tmp_zdn_base);
					Collections.reverse(tmp_zdn_base);

					//RAND()発生：確率の大きいところに入りやすい仕様
					int k = rd.nextInt((int)topicNum);
					double tmp_zdn_nume = 0.;
					for (int l = 0; l < tmp_zdn_base.size(); l++) {
						tmp_zdn_nume += tmp_zdn_base.get(l);
						if ((double)k / (double)topicNum < tmp_zdn_nume / tmp_zdn_dinomina) {
							int pos = tmp_zdn.indexOf(tmp_zdn_base.get(l));
							zn.set(j, pos);
							if (i == RUN_TIME - 1) {
								t_Nk.set(pos, t_Nk.get(pos) + 1);
								t_Nkv.get(nkv.get(j)).set(pos, t_Nkv.get(nkv.get(j)).get(pos) + 1);
							}
							break;
						}
					}

					//N(カウント)の更新
					zdn = zn.get(j);
					ndk.put(zdn, ndk.get(zdn) + 1);
				}
			}
			t_zn.add(zn);
			t_nkv.add(nkv);
			t_ndk.add(ndk);
		}
		System.out.println("-TestData Vocab : " + memo_nkv.size());
		System.out.println("-TestData WordCount : " + Ndtest);

		//logp計算
		for (int i = 0; i < origin.size(); i++) {
			double[] phai = new double[t_zn.get(i).size()];
			double p = 1.;//logp
			for (int j = 0; j < t_zn.get(i).size(); j++) {
				for (int k = 0; k < topicNum; k++) {
					phai[j] += (t_ndk.get(i).get(k) + alphak.get(k)) / ((double)t_zn.get(i).size() + alphak.get(k) * (double)topicNum) *
							(t_Nkv.get(t_nkv.get(i).get(j)).get(k) + beta) / (t_Nk.get(k) + beta * V);
				}
				p *= phai[j];
			}
			if (p != 0.) {
				logp += Math.log(p);
			} else {
				System.out.println("【exception: log(p) infinite】");
				result[0] = 1.;
			}
		}
		result[1] = Math.exp(-1. * logp / Ndtest);

		return result;
	}

}
