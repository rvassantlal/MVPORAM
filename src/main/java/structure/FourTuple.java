package structure;

import java.io.Serializable;

public class FourTuple<T1,T2,T3,T4> implements Serializable {
	private static final long serialVersionUID = 2039839532055973338L;
	private T1 first;
	private T2 second;
	private T3 third;
	private T4 fourth;
	
	public FourTuple(T1 fst,T2 snd, T3 trd, T4 frth) {
		first=fst;
		second=snd;
		third=trd;
		fourth=frth;
	}

	public T1 getFirst() {
		return first;
	}

	public T2 getSecond() {
		return second;
	}

	public T3 getThird() {
		return third;
	}
	
	public T4 getFourth() {
		return fourth;
	}
	
}
