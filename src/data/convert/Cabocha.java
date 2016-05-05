package data.convert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Random;

public class Cabocha {
	String cabochaPath = "cabocha.exe -f1";//cabochaの場所
	String command = cabochaPath;
	Process process = null;
	//かぼちゃの実行予定回数
	private static int cab_exe_cnt;
	//現在のかぼちゃ実行回数
	private static int now_cab = 0;

	//単語・品詞・品詞詳細・原形・位置・係り先・活用形を保持
	private ArrayList<String> word;
	private ArrayList<String> hinshi;
	private ArrayList<String> detailhinshi;
	private ArrayList<String> origin;
	private ArrayList<Integer> position;
	private ArrayList<Integer> kakarisaki;
	private ArrayList<String> katuyo;
	private ArrayList<String> yomigana;
	public ArrayList<ArrayList<String>> words;
	public ArrayList<ArrayList<String>> hinshis;
	public ArrayList<ArrayList<String>> detailhinshis;
	public ArrayList<ArrayList<String>> origins;
	public ArrayList<ArrayList<Integer>> positions;
	public ArrayList<ArrayList<Integer>> kakarisakis;
	public ArrayList<ArrayList<String>> katuyos;
	public ArrayList<ArrayList<String>> yomiganas;

	/*
	 *変数を渡す関数群
	 * executeCabochaを実行した後に使うこと
	 */
	public ArrayList<String> getword() {
		return word;
	}
	public ArrayList<String> gethinshi() {
		return hinshi;
	}
	public ArrayList<String> getdetailhinshi() {
		return detailhinshi;
	}
	public ArrayList<String> getorigin() {
		return origin;
	}
	public ArrayList<Integer> getposition() {
		return position;
	}
	public ArrayList<Integer> getkakarisaki() {
		return kakarisaki;
	}
	public ArrayList<String> getkatuyo() {
		return katuyo;
	}
	public ArrayList<String> getyomigana() {
		return yomigana;
	}

	/*txtなげて加工してtxtに吐き出す
	 */
	public void getcabocharesult(ArrayList<String> sentence) {
		Random r = new Random();
		String serialcode = String.valueOf(r.nextInt(99999999));
		String path = "/usr/local/bin/cabocha";
		String pathinput = "/cabocha/" + serialcode + "_inputtext.txt";
		String output ="--output=/cabocha/" + serialcode + "_output.txt";
		String filename = output.replace("--output=", "");
		String[] cmd = {path,"-f1", pathinput, output};
		this.newdatas();
		this.makeinputfile(sentence, pathinput);

		//かぼちゃ実行
		try {
			Process p = Runtime.getRuntime().exec(cmd);
			if (p.waitFor() == 0) {
			} else {
				System.exit(-1);
			}
		} catch (IOException | InterruptedException e) {
			System.out.println("-cabocha失敗");
			e.printStackTrace();
		}

		ArrayList<String> tmp_word = new ArrayList<String>();
		try {
			File file = new File(filename);
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"));
			String line;
			while ((line = br.readLine()) != null) {
				if(line.equals("EOS")) {
					this.newdata();
					this.ExchangeCabRestoList2(tmp_word);
					this.adddatas();
					tmp_word.clear();
				} else {
					tmp_word.add(line);
				}
			}
			br.close();
			File inputfile = new File(pathinput); inputfile.delete();
			File outputfile = new File(filename);outputfile.delete();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}
	private void newdata() {
		word = new ArrayList<String>();
		hinshi = new ArrayList<String>();
		detailhinshi = new ArrayList<String>();
		origin = new ArrayList<String>();
		position = new ArrayList<Integer>();
		kakarisaki = new ArrayList<Integer>();
		katuyo = new ArrayList<String>();
		yomigana = new ArrayList<String>();
	}
	private void newdatas() {
		words = new ArrayList<ArrayList<String>>();
		hinshis = new ArrayList<ArrayList<String>>();
		detailhinshis = new ArrayList<ArrayList<String>>();
		origins = new ArrayList<ArrayList<String>>();
		positions = new ArrayList<ArrayList<Integer>>();
		kakarisakis = new ArrayList<ArrayList<Integer>>();
		katuyos = new ArrayList<ArrayList<String>>();
		yomiganas = new ArrayList<ArrayList<String>>();
	}
	private void makeinputfile(ArrayList<String> sentence, String filename) {
		File outputFile = new File(filename);
		try{
			FileOutputStream fos = new FileOutputStream(outputFile);
			OutputStreamWriter osw = new OutputStreamWriter(fos, "utf-8");
			PrintWriter pw = new PrintWriter(osw);
			for(String s : sentence) {
				s = s.replace("", " ");
				pw.println(s);
			}
			pw.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	private void adddatas() {
		words.add(word);
		hinshis.add(hinshi);
		detailhinshis.add(detailhinshi);
		origins.add(origin);
		positions.add(position);
		kakarisakis.add(kakarisaki);
		katuyos.add(katuyo);
		yomiganas.add(yomigana);
	}

	/*
	 * 詳細情報まで取得するためcabochaを実行する関数
	 * 引数；センテンス
	 */
	public void executeCabochaT (String sentence) {
		int word_sup = 20;
		//エクゼが呼ばれる度にリストを新調する
		word = new ArrayList<String>(word_sup);
		hinshi = new ArrayList<String>(word_sup);
		detailhinshi =  new ArrayList<String>(word_sup);
		origin = new ArrayList<String>(word_sup);
		position = new ArrayList<Integer>(word_sup);
		kakarisaki = new ArrayList<Integer>(word_sup);
		katuyo = new ArrayList<String> (word_sup);
		yomigana = new ArrayList<String>(word_sup);

		//cabochaの結果を返す変数
		ArrayList<String> wordslist = new ArrayList<String>(word_sup);
		try {
			//cabochaの機動
			process = Runtime.getRuntime().exec(command);
		    OutputStream out= process.getOutputStream();
		    OutputStreamWriter writer = new OutputStreamWriter(out, "utf-8");
		    writer.write(sentence);
		    writer.close();
		    InputStream is = process.getInputStream();
		    BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"));
		    String line;
		    while ((line = br.readLine()) != null) {
		    	wordslist.add(line);
		    }
		    //最後にデストロイ
		    if (cab_exe_cnt == now_cab + 1) {
			    process.destroy();
			    process.waitFor();
			    is.close();
			    out.close();
			    process.getOutputStream().close();
			    process.getErrorStream().close();
		    } else {
		    	now_cab++;
		    }
		} catch( Exception e ) {
		    System.out.println(e);
		}

		//cabochaの結果を加工
		this.ExchangeCabRestoList(wordslist);

	}

	/*cabochaから得られたList形式のデータを使える状態に加工する関数
	 */
	private void ExchangeCabRestoList(ArrayList<String> word_cab) {
		int temp_position = 0;
		int temp_kakarisaki = 0;
		String str = "EOS";

		//ここで整形
		for (int i = 0; i < word_cab.size() - 1; i++){
			if(word_cab.get(i).equals(str)){
			}else{
				//一行目の*で始まる情報の処理
				if(word_cab.get(i).substring(0, 1).indexOf("*") != -1 && word_cab.get(i).indexOf("¥t") == -1){
					String[] array = word_cab.get(i).split(" ");
					temp_position = Integer.parseInt(array[1]);
					temp_kakarisaki = Integer.parseInt(array[2].replace("D", ""));
				//二列目以降の情報の処理
				}else if(word_cab.get(i).substring(0, 1).indexOf("*") == -1 || word_cab.get(i).indexOf("¥t") != -1){
					String[] str1 = word_cab.get(i).split("¥t");//ワードを格納する配列
					String[] str2 = str1[1].split(",");//cabochaの結果からタブ以下の情報を格納数配列群
					word.add(str1[0]);
					hinshi.add(str2[0]);
					detailhinshi.add(str2[1]);
					if(str2[6].equals("*")) {
						origin.add(str1[0]);
					} else {
						origin.add(str2[6]);//原形が"*"のときはwordと同じものを入れる
					}
					katuyo.add(str2[5]);
					if (str2.length < 8) {
						yomigana.add(origin.get(origin.size() - 1));//読み方がないときは原型を入れる
					} else {
						yomigana.add(str2[7]);
					}
					position.add(temp_position);
					kakarisaki.add(temp_kakarisaki);
				}
			}
		}
	}
	private void ExchangeCabRestoList2(ArrayList<String> word_cab) {
		int temp_position = 0;
		int temp_kakarisaki = 0;
		String str = "EOS";

		//ここで整形
		for (int i = 0; i < word_cab.size(); i++){
			if(word_cab.get(i).equals(str)){
			}else{
				//一行目の*で始まる情報の処理
				if(word_cab.get(i).substring(0, 1).indexOf("*") != -1 && word_cab.get(i).indexOf("\t") == -1){
					String[] array = word_cab.get(i).split(" ");
					temp_position = Integer.parseInt(array[1]);
					temp_kakarisaki = Integer.parseInt(array[2].replace("D", ""));
				//二列目以降の情報の処理
				}else if(word_cab.get(i).substring(0, 1).indexOf("*") == -1 || word_cab.get(i).indexOf("\t") != -1){
					String[] str1 = word_cab.get(i).split("\t");//ワードを格納する配列
					String[] str2 = str1[1].split(",");//cabochaの結果からタブ以下の情報を格納数配列群
					word.add(str1[0]);
					hinshi.add(str2[0]);
					detailhinshi.add(str2[1]);
					if(str2[6].equals("*")) {
						origin.add(str1[0]);
					} else {
						origin.add(str2[6]);//原形が"*"のときはwordと同じものを入れる
					}
					katuyo.add(str2[5]);
					if (str2.length < 8) {
						yomigana.add(origin.get(origin.size() - 1));//読み方がないときは原型を入れる
					} else {
						yomigana.add(str2[7]);
					}
					position.add(temp_position);
					kakarisaki.add(temp_kakarisaki);
				}
			}
		}
	}


	/*
	 * 詳細情報は取得せずcabochaを実行する関数
	 * 引数；センテンス
	 */
	public void executeCabochaF (String sentence) {
		int word_sup = 20;
		//エクゼが呼ばれる度にリストを新調する
		word = new ArrayList<String>(word_sup);

		//cabochaの結果を返す変数
		ArrayList<String> wordslist = new ArrayList<String>(word_sup);
		try {
			//cabochaの機動
			process = Runtime.getRuntime().exec(command);
		    OutputStream out= process.getOutputStream();
		    OutputStreamWriter writer = new OutputStreamWriter(out, "utf-8");
		    writer.write(sentence);
		    writer.close();
		    InputStream is = process.getInputStream();
		    BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"));
		    String line;
		    while ((line = br.readLine()) != null) {
		    	wordslist.add(line);
		    }
		    //最後にデストロイ
		    if (cab_exe_cnt == now_cab + 1) {
			    process.destroy();
			    process.waitFor();
			    is.close();
			    out.close();
			    process.getOutputStream().close();
			    process.getErrorStream().close();
		    } else {
		    	now_cab++;
		    }
		} catch( Exception e ) {
		    System.out.println(e);
		}

		//cabochaの結果を加工
		this.ExchangeCabRestoListF(wordslist);

	}
	/*
	 * 詳細情報を考慮しない
	 * cabochaから得られたList形式のデータを使える状態に加工する関数
	 */
	private void ExchangeCabRestoListF(ArrayList<String> word_cab) {
		String str = "EOS";

		//ここで整形
		for (int i = 0; i < word_cab.size() - 1; i++){
			if(word_cab.get(i).equals(str)){
			}else{
				//一行目の*で始まる情報の処理
				if(word_cab.get(i).substring(0, 1).indexOf("*") != -1 && word_cab.get(i).indexOf("¥t") == -1){
				//二列目以降の情報の処理
				}else if(word_cab.get(i).substring(0, 1).indexOf("*") == -1 || word_cab.get(i).indexOf("¥t") != -1){
					String[] str1 = word_cab.get(i).split("¥t");//ワードを格納する配列
					word.add(str1[0]);
				}
			}
		}
	}

}
