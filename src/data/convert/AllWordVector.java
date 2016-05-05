package data.convert;

import java.io.Serializable;
import java.util.ArrayList;

public class AllWordVector implements Serializable {
	private static final long serialVersionUID = -7998604799447488374L;
	
	public ArrayList<String> CollectWrd;
	public ArrayList<Integer> CollectCNT;
	public ArrayList<String> CollectHnsh;
	public ArrayList<String> CollectDHnsh;

	public ArrayList<String> getAllwordList() {
		return CollectWrd;
	}
	public ArrayList<String> getAllhinshiList() {
		return CollectHnsh;
	}
	public ArrayList<String> getAllDhinshiList() {
		return CollectDHnsh;
	}
	public ArrayList<Integer> getAllcountList() {
		return CollectCNT;
	}

	public void CollectWord_H(ArrayList<ArrayList<String>> word,
			ArrayList<ArrayList<String>> hinshi, ArrayList<ArrayList<String>> dhinshi) {

		CollectWrd = new ArrayList<String>(word.size());
		CollectCNT = new ArrayList<Integer>(word.size());
		CollectHnsh = new ArrayList<String>(word.size());
		CollectDHnsh = new ArrayList<String>(word.size());
		CollectWrd.add(word.get(0).get(0));
		CollectCNT.add(0);
		CollectHnsh.add(hinshi.get(0).get(0));
		CollectDHnsh.add(dhinshi.get(0).get(0));

		for(int i = 0; i < word.size(); i++) {
			for(int j = 0; j < word.get(i).size(); j++) {
				if(!CollectWrd.contains(word.get(i).get(j))) {
					CollectWrd.add(word.get(i).get(j));
					CollectCNT.add(1);
					CollectHnsh.add(hinshi.get(i).get(j));
					CollectDHnsh.add(dhinshi.get(i).get(j));
				} else {
					int s = -1;
					int smatch = -1;
					boolean equal = false;
					do {
						s++;
						if(word.get(i).get(j).equals(CollectWrd.get(s))
								&& hinshi.get(i).get(j).equals(CollectHnsh.get(s))
								&& dhinshi.get(i).get(j).equals(CollectDHnsh.get(s))) {
							smatch = s;
							equal = true;
						} else if(word.get(i).get(j).equals(CollectWrd.get(s))
								&& !hinshi.get(i).get(j).equals(CollectHnsh.get(s))){
							smatch = s;
						} else if(word.get(i).get(j).equals(CollectWrd.get(s))
								&& hinshi.get(i).get(j).equals(CollectHnsh.get(s))
								&& !dhinshi.get(i).get(j).equals(CollectDHnsh.get(s))) {
							smatch = s;
						}
					} while(!equal && s != CollectWrd.size() - 1);

					if(equal) {
						CollectCNT.set(smatch, CollectCNT.get(smatch) + 1);
					} else {
						CollectWrd.add(word.get(i).get(j));
						CollectCNT.add(1);
						CollectHnsh.add(hinshi.get(i).get(j));
						CollectDHnsh.add(dhinshi.get(i).get(j));
					}
				}
			}
		}
	}
}
