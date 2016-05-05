package topicmodel;

import java.io.File;
import java.io.FileNotFoundException;
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

public class Layer_TopicModel implements Serializable {
	private static final long serialVersionUID = 6255752248513019027L;

	/*
	 入力データ*/
	private ArrayList<String> sentence;
	private ArrayList<ArrayList<String>> origin;
	private ArrayList<ArrayList<String>> hinshi;
	private ArrayList<ArrayList<String>> dhinshi;
	private ArrayList<String> index  = null;
	public double topic_S;
	public double topic_K;

	/*
	 出力データ*/
	ArrayList<ArrayList<Integer>> ZSdn;
	ArrayList<ArrayList<Integer>> ZKdn;//ワードが所属するtopicKNumber
	public ArrayList<ArrayList<String>> keywordS;//{topic,頻出順に並んだワード}
	public ArrayList<ArrayList<Double>> wordcntS;//{topic,↑に対応した出現数}
	public ArrayList<ArrayList<String>> keywordK;//{topic,頻出順に並んだワード}
	public ArrayList<ArrayList<Double>> wordcntK;//{topic,↑に対応した出現数}
	ArrayList<ArrayList<Double>> thetads;//θds
	ArrayList<ArrayList<Double>> thetadk;//θdk 
	ArrayList<ArrayList<ArrayList<Double>>> thetadsk;//θdsk 

	/*
	 内部計算用データ:Listの順序がtopicに対応*/
	private ArrayList<ArrayList<Double>> Nds;//{sentence順,[topic,数]}
	private ArrayList<ArrayList<Double>> Ndk;//{sentence順,[topic,数]}
	private ArrayList<HashMap<Integer, ArrayList<Double>>> Ndsk;//{sentence順, {stopic, [ktopic, 数]}}
	public ArrayList<Double> Ns;//{topic順}
	public ArrayList<Double> Nk;//{topic順}
	public AllWordVector awv;//ワード種類
	public ArrayList<ArrayList<Double>> Nsv;
	public ArrayList<ArrayList<Double>> Nkv;//各topicに含まれる数：awvに対応[ワード種類,topic]
	public double V;//単語種類数
	private int OUTPUT_NUM;//出力するワード数
	public HashMap<Integer, Integer> sortIndexS;//ソート後の対応{key:topic}{value:sortedtopic}
	public HashMap<Integer, Integer> sortIndexK;//ソート後の対応{key:topic}{value:sortedtopic}

	/*
	 hyperparameter*/
	private double init_alpha = 0.1;//alpha初期値
	private ArrayList<Double> alphas;
	private ArrayList<ArrayList<Double>> alphak;
	private double beta = 0.1;

	public Layer_TopicModel(ArrayList<String> sentence,
			ArrayList<ArrayList<String>> origin,
			ArrayList<ArrayList<String>> hinshi,
			ArrayList<ArrayList<String>> dhinshi,
			int topic_S, int topic_K,
			int OUTPUT_NUM) {
		this.sentence = new ArrayList<String>(sentence);
		this.topic_S = topic_S;
		this.topic_K = topic_K;
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
	public boolean execute_variabl_alpha(int RUN_TIME) {
		//初期化
		this.initialize();

		//while(条件満たすまで
		int counter = 0;
		while (counter < RUN_TIME) {
			counter++;

			//N,Zの更新 サンプリング
			this.renewal_Zdn();

			//hyperparameter更新
			try {
				this.renew_alpha();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			if (alphak.isEmpty()) return false;
			beta = this.renew_beta();

			int r = counter * 100 / RUN_TIME;
			if(((double)counter * 100. / (double)RUN_TIME) % 10. == 0.)
				System.out.println("【" + r + "%終了】-" + new Date().toString());
		}

		System.out.println("--パラメータ更新終了：" + new Date().toString());
		this.collect_resultS();
		this.collect_resultK();
		this.thetadk();
		this.thetads();

		return true;
	}
	
	private void initialize() {
		//Zdn,Ndk初期化：全て-1,0
		ZSdn = new ArrayList<ArrayList<Integer>>((int) V);
		ZKdn = new ArrayList<ArrayList<Integer>>((int) V);
		//Nds = new HashMap<String, ArrayList<Double>>();
		Nds = new ArrayList<ArrayList<Double>>(sentence.size());
		//Ndk = new HashMap<String, ArrayList<Double>>();
		Ndk = new ArrayList<ArrayList<Double>>(sentence.size());
		//Ndsk = new HashMap<String, HashMap<Integer, ArrayList<Double>>>();
		Ndsk = new ArrayList<HashMap<Integer, ArrayList<Double>>>(sentence.size());

		for (int i = 0; i < sentence.size(); i++) {
			ArrayList<Integer> tmpzs = new ArrayList<Integer>((int)topic_S);
			ArrayList<Integer> tmpzk = new ArrayList<Integer>((int)topic_K);
			for (int j = 0; j < origin.get(i).size(); j++) {
				tmpzs.add(-1);
				tmpzk.add(-1);
			}
			ZSdn.add(tmpzs);
			ZKdn.add(tmpzk);

			ArrayList<Double> tmpns = new ArrayList<Double>((int)topic_S);
			ArrayList<Double> tmpnk = new ArrayList<Double>((int)topic_K);
			HashMap<Integer, ArrayList<Double>> tmpnsk = new HashMap<Integer, ArrayList<Double>>((int)topic_K);
			for (int j = 0; j < topic_S; j++) {
				tmpns.add(0.);
				ArrayList<Double> tmpsk = new ArrayList<Double>((int)topic_K);
				for (int t = 0; t < topic_K; t++) tmpsk.add(0.);
				tmpnsk.put(j, tmpsk);
			}
			for (int j = 0; j < topic_K; j++) tmpnk.add(0.);
			//Nds.put(sentence.get(i), tmpns);
			Nds.add(tmpns);
			//Ndk.put(sentence.get(i), tmpnk);
			Ndk.add(tmpnk);
			//Ndsk.put(sentence.get(i), tmpnsk);
			Ndsk.add(i, tmpnsk);
		}

		//Nkv初期化：
		Nsv = new ArrayList<ArrayList<Double>>((int)V);
		Nkv = new ArrayList<ArrayList<Double>>((int)V);
		for (int i = 0; i < awv.CollectWrd.size(); i++) {
			ArrayList<Double> tmpsv = new ArrayList<Double>((int)topic_S);
			ArrayList<Double> tmpkv = new ArrayList<Double>((int)topic_K);
			for (int j = 0; j < topic_S; j++) tmpsv.add(0.);
			for (int j = 0; j < topic_K; j++) tmpkv.add(0.);
			Nsv.add(tmpsv);
			Nkv.add(tmpkv);
		}

		//Nk初期化
		Ns = new ArrayList<Double>((int)topic_S);
		Nk = new ArrayList<Double>((int)topic_K);
		for (int i = 0; i < topic_S; i++) Ns.add(0.);
		for (int i = 0; i < topic_K; i++) Nk.add(0.);

		//alpha初期化
		alphas = new ArrayList<Double>((int)topic_S);
		for (int i = 0; i < topic_S; i++) alphas.add(init_alpha);
		alphak = new ArrayList<ArrayList<Double>>((int)topic_K);
		for (int i = 0; i < topic_S; i++) {
			ArrayList<Double> tmp = new ArrayList<Double>((int)topic_K);
			for (int j = 0; j < topic_K; j++) tmp.add(init_alpha);
			alphak.add(tmp);
		}
	}
	private void renewal_Zdn() {
		Random rd;
		for (int i = 0; i < sentence.size(); i++) {
			for (int j = 0; j < origin.get(i).size(); j++) {
				int zsdn = ZSdn.get(i).get(j);
				int zkdn = ZKdn.get(i).get(j);
				//String sntnc = sentence.get(i);
				int nskv = -1;//Nkvの位置
				for (int k = 0; k < awv.CollectWrd.size(); k++) {
					if (awv.CollectWrd.get(k).equals(origin.get(i).get(j)) &&
							awv.CollectHnsh.get(k).equals(hinshi.get(i).get(j)) &&
							awv.CollectDHnsh.get(k).equals(dhinshi.get(i).get(j))) {
						nskv = k;
						break;
					}
				}

				//N(カウント)の処理
				if (zkdn != -1) {
					//Nds.get(sntnc).set(zsdn, Nds.get(sntnc).get(zsdn) - 1.);
					Nds.get(i).set(zsdn, Nds.get(i).get(zsdn) - 1.);
					//Ndk.get(sntnc).set(zkdn, Ndk.get(sntnc).get(zkdn) - 1.);
					Ndk.get(i).set(zkdn, Ndk.get(i).get(zkdn) - 1.);
					//Ndsk.get(sntnc).get(zsdn).set(zkdn, Ndsk.get(sntnc).get(zsdn).get(zkdn) - 1.);
					Ndsk.get(i).get(zsdn).set(zkdn, Ndsk.get(i).get(zsdn).get(zkdn) - 1.);
					Ns.set(zsdn, Ns.get(zsdn) - 1.);
					Nk.set(zkdn, Nk.get(zkdn) - 1.);
					Nsv.get(nskv).set(zsdn, Nsv.get(nskv).get(zsdn) - 1.);
					Nkv.get(nskv).set(zkdn, Nkv.get(nskv).get(zkdn) - 1.);
				}

				//サンプリング
				ArrayList<ArrayList<Double>> tmp_zdn = 
						new ArrayList<ArrayList<Double>>((int)topic_S);
				ArrayList<Double> tmp_base = new ArrayList<Double>((int)topic_K);
				double tmp_dinomina = 0.;

				for (int s = 0; s < topic_S; s++) {
					ArrayList<Double> tmp_tmp_zdn = new ArrayList<Double>((int)topic_K);
					double alphask =  0.;
					for (double ask : alphak.get(s)) alphask += ask;
					for (int k = 0; k < topic_K; k++) {
						double calc_zkdn = 0;
						
						calc_zkdn = (Ns.get(s) + alphas.get(s)) * 
								//(Ndsk.get(sntnc).get(s).get(k) + alphak.get(s).get(k)) /
								(Ndsk.get(i).get(s).get(k) + alphak.get(s).get(k)) /
								(Ns.get(s) + alphask) * 
								(Nkv.get(nskv).get(k) + beta) / 
								(Nk.get(k) + beta * V);

						tmp_tmp_zdn.add(calc_zkdn);
						tmp_base.add(calc_zkdn);
						tmp_dinomina += calc_zkdn;
					}
					tmp_zdn.add(tmp_tmp_zdn);
				}

				Collections.sort(tmp_base);
				Collections.reverse(tmp_base);

				//RAND()発生：確率の大きいところに入りやすい仕様
				 rd = new Random();
				 double sk = rd.nextInt((int)topic_K * (int)topic_S);
				 double tmp_nume = 0.;
				 for (int l = 0; l < tmp_base.size(); l++) {
					 tmp_nume += tmp_base.get(l);
					 if ((sk / (topic_K * topic_S)) < (tmp_nume / tmp_dinomina)) {
						 //posの見つけ方
						 int Spos = -1;
						 int Kpos = -1;
						 for (int s = 0; s < topic_S; s++) {
							 Kpos = tmp_zdn.get(s).indexOf(tmp_base.get(l));
							 if (Kpos != -1) {
								 Spos = s;
								 break;
							 }
						 }
						 ZKdn.get(i).set(j, Kpos);
						 ZSdn.get(i).set(j, Spos);
						 break;
					 }
				 }

				 //N(カウント)の更新
				 zsdn = ZSdn.get(i).get(j);
				 //Nds.get(sntnc).set(zsdn, Nds.get(sntnc).get(zsdn) + 1.);
				 Nds.get(i).set(zsdn, Nds.get(i).get(zsdn) + 1.);
				 Ns.set(zsdn, Ns.get(zsdn) + 1.);
				 Nsv.get(nskv).set(zsdn, Nsv.get(nskv).get(zsdn) + 1.);
				 zkdn = ZKdn.get(i).get(j);
				 //Ndk.get(sntnc).set(zkdn, Ndk.get(sntnc).get(zkdn) + 1.);
				 Ndk.get(i).set(zkdn, Ndk.get(i).get(zkdn) + 1.);
				 Nk.set(zkdn, Nk.get(zkdn) + 1.);
				 Nkv.get(nskv).set(zkdn, Nkv.get(nskv).get(zkdn) + 1.);
				 //Ndsk.get(sntnc).get(zsdn).set(zkdn, Ndsk.get(sntnc).get(zsdn).get(zkdn) + 1.);
				 Ndsk.get(i).get(zsdn).set(zkdn, Ndsk.get(i).get(zsdn).get(zkdn) + 1.);
			}
		}
	}

	private void renew_alpha() throws FileNotFoundException {		
		double sigmaas = 0.;
		double dinnomena = 0.;
		for (double as : alphas) sigmaas += as;
		System.out.println("sigmaas" + " " + sigmaas);
		double sigdigammas = 0.;
		try {
			sigdigammas = Gamma.digamma(sigmaas);
		} catch(java.lang.Error e) {
			System.out.println("sigdigamma " + sigmaas);
			System.exit(-1);
		}
		for (int i = 0; i < sentence.size(); i++) {
			dinnomena += Gamma.digamma((double)origin.get(i).size() + sigmaas) 
					- sigdigammas;
		}
		
		for (int s = 0; s < topic_S; s++) {
			//a0sの更新
			double newas = 0.;
			double sdigamma = 0.;
			try {
				sdigamma = Gamma.digamma(alphas.get(s));
			} catch(java.lang.Error e) {
				System.out.println("a0s : " + alphas.get(s));
				System.exit(-1);
			}
			for (int i = 0; i < sentence.size(); i++) {
				try {
					//newas += Gamma.digamma(Nds.get(sentence.get(i)).get(s) + alphas.get(s)) 
					newas += Gamma.digamma(Nds.get(i).get(s) + alphas.get(s)) 
							- sdigamma;
				} catch(java.lang.Error e) {
					//System.out.println("a0s_Nds:" + Nds.get(sentence.get(i)).get(s));
					System.out.println("a0s_Nds:" + Nds.get(i).get(s));
					System.exit(-1);
				}
			}
			
			double new_alphas = alphas.get(s) * newas / dinnomena;
			if (Double.isNaN(new_alphas) || new_alphas == 0.) new_alphas = init_alpha;
			
			alphas.set(s, new_alphas);
						
			//askの更新
			double sigalphak = 0.;//共通項Σalphak
			for (double a : alphak.get(s)) sigalphak += a;
			ArrayList<Double> tmp_alpha = new ArrayList<Double>((int)topic_K);
			double digammasigalpha = 0.;
			try {
				digammasigalpha = Gamma.digamma(sigalphak);
			} catch(java.lang.Error e) {
				System.out.println("ask : " + sigalphak);
				System.exit(-1);
			}
			
			for (int t = 0; t < topic_K; t++) {
				double new_alpha_nume = 0.;
				double new_alpha_denomina = 0.;
				
				double sigdigammask = 0.;
				try {
					sigdigammask = Gamma.digamma(alphak.get(s).get(t));
				} catch(java.lang.Error e) {
					System.out.println("ask alphak " + alphak.get(s).get(t));
					System.exit(-1);
				}
				for (int i = 0; i < sentence.size(); i++) {
					try {
						//new_alpha_nume += Gamma.digamma(Ndsk.get(sentence.get(i)).get(s).get(t) + alphak.get(s).get(t)) 
						new_alpha_nume += Gamma.digamma(Ndsk.get(i).get(s).get(t) + alphak.get(s).get(t)) 
								- sigdigammask;
						//new_alpha_denomina += Gamma.digamma(Nds.get(sentence.get(i)).get(s) + sigalphak) 
						new_alpha_denomina += Gamma.digamma(Nds.get(i).get(s) + sigalphak) 
								- digammasigalpha;
					} catch(java.lang.Error e) {
						//System.out.println("ask_ndsk nds" + Ndsk.get(sentence.get(i)).get(s).get(t) + " " + Nds.get(sentence.get(i)).get(s));
						System.out.println("ask_ndsk nds" + Ndsk.get(i).get(s).get(t) + " " + Nds.get(i).get(s));
						System.exit(-1);
					}
				}
				
				double new_alpha = alphak.get(s).get(t) * new_alpha_nume / new_alpha_denomina;
				if (Double.isNaN(new_alpha) || new_alpha == 0.) new_alpha = init_alpha;
				
				tmp_alpha.add(new_alpha);
			}
			alphak.set(s, tmp_alpha);
		}
	}
	private double renew_beta() {
		double new_beta_nume = 0.;
		double new_beta_denomina = 0.;
		
		double gamma_beta = Gamma.digamma(beta);
		double Vgamma_beta = Gamma.digamma(V * beta);
		for (int i = 0; i < topic_K; i++) {
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
	private void collect_resultK() {
		keywordK = new ArrayList<ArrayList<String>>((int)topic_K);
		wordcntK = new ArrayList<ArrayList<Double>>((int)topic_K);
		ArrayList<ArrayList<Double>> base_cnt = new ArrayList<ArrayList<Double>>((int)topic_K);

		for (int i = 0; i < topic_K; i++) {
			ArrayList<Double> tmp = new ArrayList<Double>((int)V);
			ArrayList<Double> base_tmp = new ArrayList<Double>((int)V);
			for (int j = 0; j < V; j++) {
				tmp.add(0.);
				base_tmp.add(0.);
			}
			base_cnt.add(tmp);
			wordcntK.add(base_tmp);
		}

		for (int i = 0; i < Nkv.size(); i++) {
			for (int j = 0; j < Nkv.get(i).size(); j++) {
				base_cnt.get(j).set(i, base_cnt.get(j).get(i) + Nkv.get(i).get(j));
				wordcntK.get(j).set(i, wordcntK.get(j).get(i) + Nkv.get(i).get(j));
			}
		}

		for (int i = 0; i < wordcntK.size(); i++) {
			Collections.sort(wordcntK.get(i));
			Collections.reverse(wordcntK.get(i));
		}

		//ワード数上位に絞り込み
		if (OUTPUT_NUM > -1) {
			for (int i = 0 ; i < wordcntK.size(); i++) {
				for (int j = wordcntK.get(i).size() - 1; j >= OUTPUT_NUM; j--) {
					wordcntK.get(i).remove(j);
				}
			}
		}

		for (int i = 0; i < base_cnt.size(); i++) {
			ArrayList<String> tmp = new ArrayList<String>(awv.CollectWrd.size());
			for (int j = 0; j < wordcntK.get(i).size(); j++) {
				for (int k = 0; k < base_cnt.get(i).size(); k++) {
					if (wordcntK.get(i).get(j).equals(base_cnt.get(i).get(k))) {
						if (tmp.isEmpty() || !tmp.contains(awv.CollectWrd.get(k))) {
							tmp.add(awv.CollectWrd.get(k));
							break;
						}
					}
				}
			}
			keywordK.add(tmp);
		}
	}
	private void collect_resultS() {
		keywordS = new ArrayList<ArrayList<String>>((int)topic_S);
		wordcntS = new ArrayList<ArrayList<Double>>((int)topic_S);
		ArrayList<ArrayList<Double>> base_cnt = new ArrayList<ArrayList<Double>>((int)topic_S);

		for (int i = 0; i < topic_S; i++) {
			ArrayList<Double> tmp = new ArrayList<Double>((int)V);
			ArrayList<Double> base_tmp = new ArrayList<Double>((int)V);
			for (int j = 0; j < V; j++) {
				tmp.add(0.);
				base_tmp.add(0.);
			}
			base_cnt.add(tmp);
			wordcntS.add(base_tmp);
		}

		for (int i = 0; i < Nsv.size(); i++) {
			for (int j = 0; j < Nsv.get(i).size(); j++) {
				base_cnt.get(j).set(i, base_cnt.get(j).get(i) + Nsv.get(i).get(j));
				wordcntS.get(j).set(i, wordcntS.get(j).get(i) + Nsv.get(i).get(j));
			}
		}

		for (int i = 0; i < wordcntS.size(); i++) {
			Collections.sort(wordcntS.get(i));
			Collections.reverse(wordcntS.get(i));
		}

		//ワード数上位に絞り込み
		if (OUTPUT_NUM > -1) {
			for (int i = 0 ; i < wordcntS.size(); i++) {
				for (int j = wordcntS.get(i).size() - 1; j >= OUTPUT_NUM; j--) {
					wordcntS.get(i).remove(j);
				}
			}
		}

		for (int i = 0; i < base_cnt.size(); i++) {
			ArrayList<String> tmp = new ArrayList<String>(awv.CollectWrd.size());
			for (int j = 0; j < wordcntS.get(i).size(); j++) {
				for (int k = 0; k < base_cnt.get(i).size(); k++) {
					if (wordcntS.get(i).get(j).equals(base_cnt.get(i).get(k))) {
						if (tmp.isEmpty() || !tmp.contains(awv.CollectWrd.get(k))) {
							tmp.add(awv.CollectWrd.get(k));
							break;
						}
					}
				}
			}
			keywordS.add(tmp);
		}
	}

	//各トピック上位ワード再集計
	public void Recollect_resultK(int OUTPUT_NUM) {//OUTPUT_NUM < -1 で全ワード
		keywordK = new ArrayList<ArrayList<String>>((int)topic_K);
		wordcntK = new ArrayList<ArrayList<Double>>((int)topic_K);
		ArrayList<ArrayList<Double>> base_cnt = new ArrayList<ArrayList<Double>>((int)topic_K);

		for (int i = 0; i < topic_K; i++) {
			ArrayList<Double> tmp = new ArrayList<Double>((int)V);
			ArrayList<Double> base_tmp = new ArrayList<Double>((int)V);

			for (int j = 0; j < V; j++) {
				tmp.add(0.);
				base_tmp.add(0.);
			}

			base_cnt.add(tmp);
			wordcntK.add(base_tmp);
		}

		for (int i = 0; i < Nkv.size(); i++) {
			for (int j = 0; j < Nkv.get(i).size(); j++) {
				base_cnt.get(j).set(i, base_cnt.get(j).get(i) + Nkv.get(i).get(j));
				wordcntK.get(j).set(i, wordcntK.get(j).get(i) + Nkv.get(i).get(j));
			}
		}

		for (int i = 0; i < wordcntK.size(); i++) {
			Collections.sort(wordcntK.get(i));
			Collections.reverse(wordcntK.get(i));
		}

		//ワード数上位に絞り込み
		if (OUTPUT_NUM > -1) {
			for (int i = 0 ; i < wordcntK.size(); i++) {
				for (int j = wordcntK.get(i).size() - 1; j >= OUTPUT_NUM; j--) {
					wordcntK.get(i).remove(j);
				}
			}
		}

		for (int i = 0; i < base_cnt.size(); i++) {
			ArrayList<String> tmp = new ArrayList<String>(awv.CollectWrd.size());
			for (int j = 0; j < wordcntK.get(i).size(); j++) {
				for (int k = 0; k < base_cnt.get(i).size(); k++) {
					if (wordcntK.get(i).get(j).equals(base_cnt.get(i).get(k))) {
						if (tmp.isEmpty() || !tmp.contains(awv.CollectWrd.get(k))) {
							tmp.add(awv.CollectWrd.get(k));
							break;
						}
					}
				}
			}
			keywordK.add(tmp);
		}
	}

	public void outputK(String url) {
		File outputFile = new File(url);
		try{
			FileOutputStream fos = new FileOutputStream(outputFile);
			OutputStreamWriter osw = new OutputStreamWriter(fos);
			PrintWriter pw = new PrintWriter(osw);

			for (int i = 1; i <= topic_K; i++) {
				pw.print("Topic" + i + ",出現数,");
			}pw.println("");

			if (sortIndexK == null) this.sortK();
			for (int i  =0; i < OUTPUT_NUM; i++) {
				for (int j = 0; j < keywordK.size(); j++) {
					if (keywordK.get(j).size() > i && wordcntK.get(j).get(i) > 0) {
						pw.print(keywordK.get(j).get(i) + "," + wordcntK.get(j).get(i) + ",");
					} else {
						pw.print(",,");
					}
				}
				pw.println("");
			}
			pw.println();
			for (int i = 0; i < keywordK.size(); i++) {
				pw.println(keywordK.get(i).get(0) + "," + wordcntK.get(i).get(0));
			}

			pw.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	private void sortK() {
		if (keywordK == null) this.collect_resultK();
		ArrayList<ArrayList<String>> tmp_k = new ArrayList<ArrayList<String>>(keywordK.size());
		ArrayList<ArrayList<Double>> tmp_c = new ArrayList<ArrayList<Double>>(wordcntK.size());
		for (int i = 0; i < keywordK.size(); i++) {
			tmp_k.add(keywordK.get(i));
			tmp_c.add(wordcntK.get(i));
		}
		sortIndexK = new HashMap<Integer, Integer>((int)topic_K);

		ArrayList<Double> order = new ArrayList<Double>(wordcntK.size());
		for (int i = 0; i < wordcntK.size(); i++) {
			order.add(wordcntK.get(i).get(0));
		}

		Collections.sort(order);
		Collections.reverse(order);

		ArrayList<ArrayList<String>> sorted_k = new ArrayList<ArrayList<String>>(keywordK.size());
		ArrayList<ArrayList<Double>> sorted_c = new ArrayList<ArrayList<Double>>(wordcntK.size());

		for (int i = 0; i < order.size(); i++) {
			ArrayList<Integer> memo = new ArrayList<Integer>((int)topic_K);
			for (int j = 0; j < order.size(); j++) {
				if (order.get(i) == tmp_c.get(j).get(0) && !memo.contains(j)) {
					memo.add(j);
					sorted_k.add(tmp_k.get(j));
					sorted_c.add(tmp_c.get(j));
					sortIndexK.put(i, j);
				}
			}
		}

		keywordK = new ArrayList<ArrayList<String>>(sorted_k);
		wordcntK = new ArrayList<ArrayList<Double>>(sorted_c);
	}
	public void outputS(String url) {
		File outputFile = new File(url);
		try{
			FileOutputStream fos = new FileOutputStream(outputFile);
			OutputStreamWriter osw = new OutputStreamWriter(fos);
			PrintWriter pw = new PrintWriter(osw);

			for (int i = 1; i <= topic_S; i++) {
				pw.print("Topic" + i + ",出現数,");
			}pw.println("");

			if (sortIndexS == null) this.sortS();
			for (int i  =0; i < OUTPUT_NUM; i++) {
				for (int j = 0; j < keywordS.size(); j++) {
					if (keywordS.get(j).size() > i && wordcntS.get(j).get(i) > 0) {
						pw.print(keywordS.get(j).get(i) + "," + wordcntS.get(j).get(i) + ",");
					} else {
						pw.print(",,");
					}
				}
				pw.println("");
			}
			pw.println();
			for (int i = 0; i < keywordS.size(); i++) {
				pw.println(keywordS.get(i).get(0) + "," + wordcntS.get(i).get(0));
			}

			pw.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	private void sortS() {
		if (keywordS == null) this.collect_resultS();
		ArrayList<ArrayList<String>> tmp_k = new ArrayList<ArrayList<String>>(keywordS.size());
		ArrayList<ArrayList<Double>> tmp_c = new ArrayList<ArrayList<Double>>(wordcntS.size());
		for (int i = 0; i < keywordS.size(); i++) {
			tmp_k.add(keywordS.get(i));
			tmp_c.add(wordcntS.get(i));
		}
		sortIndexS = new HashMap<Integer, Integer>((int)topic_S);

		ArrayList<Double> order = new ArrayList<Double>(wordcntS.size());
		for (int i = 0; i < wordcntS.size(); i++) {
			order.add(wordcntS.get(i).get(0));
		}

		Collections.sort(order);
		Collections.reverse(order);

		ArrayList<ArrayList<String>> sorted_k = new ArrayList<ArrayList<String>>(keywordS.size());
		ArrayList<ArrayList<Double>> sorted_c = new ArrayList<ArrayList<Double>>(wordcntS.size());

		for (int i = 0; i < order.size(); i++) {
			ArrayList<Integer> memo = new ArrayList<Integer>((int)topic_S);
			for (int j = 0; j < order.size(); j++) {
				if (order.get(i) == tmp_c.get(j).get(0) && !memo.contains(j)) {
					memo.add(j);
					sorted_k.add(tmp_k.get(j));
					sorted_c.add(tmp_c.get(j));
					sortIndexS.put(i, j);
				}
			}
		}

		keywordS = new ArrayList<ArrayList<String>>(sorted_k);
		wordcntS = new ArrayList<ArrayList<Double>>(sorted_c);
	}

	//各センテンスのトピック依存確率
	private void thetads() {
		thetads = new ArrayList<ArrayList<Double>>(sentence.size());

		for (int i = 0; i < sentence.size(); i++) {
			ArrayList<Double> tmps = new ArrayList<Double>((int)topic_S);
			
			for (int s = 0; s < topic_S; s++) {

				//double a = (Nds.get(sentence.get(i)).get(s) + alphas.get(s)) / 
				double a = (Nds.get(i).get(s) + alphas.get(s)) / 
						(origin.get(i).size() + alphas.get(s) * topic_S);
				tmps.add(a);
			}
			thetads.add(tmps);
		}
	}
	private void thetadk() {
		thetadk = new ArrayList<ArrayList<Double>>(sentence.size());
		thetadsk = new ArrayList<ArrayList<ArrayList<Double>>>(sentence.size());

		for (int i = 0; i < sentence.size(); i++) {
			ArrayList<ArrayList<Double>> tmps = new ArrayList<ArrayList<Double>>((int)topic_S);
			ArrayList<Double> tmpk = new ArrayList<Double>((int)topic_K);
			
			for (int s = 0; s < topic_S; s++) {
				ArrayList<Double> tmp = new ArrayList<Double>((int)topic_K);
				
				for (int k = 0; k < topic_K; k++) {
					//double a = (Ndsk.get(sentence.get(i)).get(s).get(k) + alphak.get(s).get(k)) / 
					double a = (Ndsk.get(i).get(s).get(k) + alphak.get(s).get(k)) / 
							//(Nds.get(sentence.get(i)).size() + alphak.get(s).get(k) * topic_K);
							(Nds.get(i).size() + alphak.get(s).get(k) * topic_K);
					tmp.add(a);
					if (tmpk.size() > k) {
						tmpk.set(k, tmpk.get(k) + a);
					} else {
						tmpk.add(a);
					}
				}
				tmps.add(tmp);
			}
			thetadk.add(tmpk);
			thetadsk.add(tmps);
		}
	}
	
	//各トピック所属センテンス
	private HashMap<String, HashMap<ArrayList<String>, ArrayList<Double>>> topicS_contents(int contentsnum) {
		int C_NUM = contentsnum;
		if (thetads == null) return null;
		if (index == null) {
			index = new ArrayList<String>(thetads.size());
			for (int i = 0; i < thetads.size(); i++) index.add(String.valueOf(i));
		}
		if (thetads.size() < contentsnum) C_NUM = thetads.size();
		HashMap<String, HashMap<ArrayList<String>, ArrayList<Double>>> contents =
				new HashMap<String, HashMap<ArrayList<String>, ArrayList<Double>>>((int)topic_S);
		for (int i = 0; i < topic_S; i++) {
			ArrayList<Double> tmp = new ArrayList<Double>(thetads.size());
			for (int j = 0; j < thetads.size(); j++) {
				tmp.add(thetads.get(j).get(i));
			}
			Collections.sort(tmp);Collections.reverse(tmp);
			for (int j = tmp.size() - 1; j >= C_NUM; j--) tmp.remove(j);

			ArrayList<String> tmpc = new ArrayList<String>(C_NUM);
			ArrayList<Integer> memo = new ArrayList<Integer>(C_NUM);
			for (int j = 0; j < C_NUM; j++) {
				for (int s = 0; s < thetads.size(); s++) {
					if (tmp.get(j) == thetads.get(s).get(i) && !memo.contains(s)) {
						tmpc.add(index.get(s));
						memo.add(s);
						break;
					} else if (tmp.get(j) == thetads.get(s).get(i)) {
						System.out.println();
					}
					if(s == thetads.size() - 1) {
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
	private HashMap<String, HashMap<ArrayList<String>, ArrayList<Double>>> topicK_contents(int contentsnum) {
		int C_NUM = contentsnum;
		if (thetadk == null) return null;
		if (index == null) {
			index = new ArrayList<String>(thetadk.size());
			for (int i = 0; i < thetadk.size(); i++) index.add(String.valueOf(i));
		}
		if (thetadk.size() < contentsnum) C_NUM = thetadk.size();
		HashMap<String, HashMap<ArrayList<String>, ArrayList<Double>>> contents =
				new HashMap<String, HashMap<ArrayList<String>, ArrayList<Double>>>((int)topic_K);
		for (int i = 0; i < topic_K; i++) {
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
	public void outputcontentsS(int contentsnum, String filepath) {
		HashMap<String, HashMap<ArrayList<String>, ArrayList<Double>>> contents =
				new HashMap<String, HashMap<ArrayList<String>, ArrayList<Double>>>(this.topicS_contents(contentsnum));

		File of = new File(filepath);
		try {
			FileOutputStream fos = new FileOutputStream(of);
			OutputStreamWriter osw = new OutputStreamWriter(fos);
			PrintWriter pw = new PrintWriter(osw);

			for (int i = 1; i < topic_S; i++) pw.print("Topic" + i + ",確率,");
			pw.println("Topic" + topic_S + ",確率");

			int sz = -1;
			for (int i = 0; i < contentsnum; i++) {
				if (i == sz) break;
				for (int j = 0; j < topic_S - 1; j++) {
					for (ArrayList<String> c : contents.get(String.valueOf(sortIndexS.get(j))).keySet()) {
						if (c.size() < i - 1) {
							pw.print(",,");
						} else {
							pw.print(c.get(i) + ","+ contents.get(String.valueOf(sortIndexS.get(j))).get(c).get(i) + ",");
						}
						sz = c.size();
					}
				}

				for (ArrayList<String> c : contents.get(String.valueOf(sortIndexS.get((int)topic_S - 1))).keySet()) {
					if (c.size() < i - 1) {
						pw.print(",,");
					} else {
						pw.println(c.get(i) + "," + contents.get(String.valueOf(sortIndexS.get((int)topic_S - 1))).get(c).get(i));
					}
				}
			}
			pw.close();
		} catch(Exception e) {
			e.printStackTrace();
		}

	}
	public void outputcontentsK(int contentsnum, String filepath) {
		HashMap<String, HashMap<ArrayList<String>, ArrayList<Double>>> contents =
				new HashMap<String, HashMap<ArrayList<String>, ArrayList<Double>>>(this.topicK_contents(contentsnum));

		File of = new File(filepath);
		try {
			FileOutputStream fos = new FileOutputStream(of);
			OutputStreamWriter osw = new OutputStreamWriter(fos);
			PrintWriter pw = new PrintWriter(osw);

			for (int i = 1; i < topic_K; i++) pw.print("Topic" + i + ",確率,");
			pw.println("Topic" + topic_K + ",確率");

			int sz = -1;
			for (int i = 0; i < contentsnum; i++) {
				if (i == sz) break;
				for (int j = 0; j < topic_K - 1; j++) {
					for (ArrayList<String> c : contents.get(String.valueOf(sortIndexK.get(j))).keySet()) {
						if (c.size() < i - 1) {
							pw.print(",,");
						} else {
							pw.print(c.get(i) + ","+ contents.get(String.valueOf(sortIndexK.get(j))).get(c).get(i) + ",");
						}
						sz = c.size();
					}
				}

				for (ArrayList<String> c : contents.get(String.valueOf(sortIndexK.get((int)topic_K - 1))).keySet()) {
					if (c.size() < i - 1) {
						pw.print(",,");
					} else {
						pw.println(c.get(i) + "," + contents.get(String.valueOf(sortIndexK.get((int)topic_K - 1))).get(c).get(i));
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
		if (sortIndexS == null) this.sortS();
		if (sortIndexK == null) this.sortK();
		FileOutputStream outFile = new FileOutputStream(outputfile);
		ObjectOutputStream oos = new ObjectOutputStream(outFile);
		oos.writeObject(this);
		oos.close();
	}
	
}
