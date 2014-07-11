package org.libbun.peg4d;

import java.util.HashMap;

import org.libbun.UList;
import org.libbun.UMap;

public class SimpleParserContext extends ParserContext {
	private UMap<Peg>        pegCache;
	
	class SimpleMemo {
		PegObject result;
		int nextPosition;
	}

	final UList<SimpleLog> logStack = new UList<SimpleLog>(new SimpleLog[128]);
	private int stackTop = 0;

	class SimpleLog {
		long sourcePosition;
		Peg trace;
		char type;
		PegObject parentNode;
		int index;
		PegObject childNode;
	}
	
	public SimpleParserContext(ParserSource source) {
		this(source, 0, source.length());
		this.initMemo();
	}

	public SimpleParserContext(ParserSource source, long startIndex, long endIndex) {
		super(source, startIndex, endIndex);
	}
	
	@Override
	public void setRuleSet(PegRuleSet ruleSet) {
		this.ruleSet = ruleSet;
		this.loadPegDefinition(ruleSet.pegMap);
	}

	public final void loadPegDefinition(UMap<Peg> pegMap) {
		this.pegCache = new UMap<Peg>();	
		UList<String> list = pegMap.keys();
		for(int i = 0; i < list.size(); i++) {
			String key = list.ArrayValues[i];
			Peg e = pegMap.get(key, null);
			this.checkLeftRecursion(key, e);
		}
	}
	
	private void checkLeftRecursion(String name, Peg e) {
		if(e instanceof PegChoice) {
			for(int i = 0; i < e.size(); i++) {
				this.checkLeftRecursion(name, e.get(i));
			}
			return;
		}
		if(e instanceof PegSequence) {
			PegSequence seq = (PegSequence)e;
			if(seq.size() > 1) {
				Peg first = seq.get(0);
				if(first instanceof PegLabel) {
					String label = ((PegLabel) first).symbol;
					if(label.equals(name)) {
						//this.lrExistence = true;
						String key = this.nameRightJoinName(name);  // left recursion
						this.appendPegCache(key, seq.cdr());
						return;
					}
				}
			}
		}
		this.appendPegCache(name, e);
	}
	
	private String nameRightJoinName(String name) {
		return name + "+";
	}

	private void appendPegCache(String name, Peg e) {
		Peg defined = this.pegCache.get(name, null);
		if(defined != null) {
			e = defined.appendAsChoice(e);
		}
		this.pegCache.put(name, e);
	}
		
	@Override
	public SourceContext subContext(long startIndex, long endIndex) {
		return new SimpleParserContext(this.source, startIndex, endIndex);
	}
		
	public final Peg getRule(String name) {
		return this.pegCache.get(name, null);
	}

	private final Peg getRightJoinRule(String name) {
		return this.pegCache.get(this.nameRightJoinName(name), null);
	}

	public final PegObject parsePegObject(PegObject parentNode, String ruleName) {
		Peg e = this.getRule(ruleName);
		PegObject left = e.performMatch(parentNode, this);
		if(left.isFailure()) {
			return left;
		}
		e = this.getRightJoinRule(ruleName);
		if(e != null) {
			return e.performMatch(left, this);
		}
		return left;
	}
	
	private class Memo2 {
		Peg keypeg;
		long pos;
		Peg createdPeg;
		Memo2 next;
	}
	
	private HashMap<Long, Memo2> memoMap2;

	public void initMemo() {
		this.memoMap2 = new HashMap<Long, Memo2>();
	}
	
	public void clearMemo() {}
	
	int memoHit = 0;
	int memoMiss = 0;
	int memoSize = 0;

	private Memo2 getPreCheckCache(Peg keypeg, long keypos) {
		Memo2 m = this.memoMap2.get(keypos);
		while(m != null) {
			if(m.keypeg == keypeg) {
				return m;
			}
			m = m.next;
		}
		return m;
	}

	public void removeMemo(long startIndex, long endIndex) {
		if(this.memoMap2 != null) {
			//System.out.println("remove = " + startIndex + ", " + endIndex);
			for(long i = startIndex; i < endIndex; i++) {
				Long key = i;
				Memo2 m = this.memoMap2.get(key);
				if(m != null) {
					appendMemo2(m, this.UnusedMemo);
					this.UnusedMemo = m;
					this.memoMap2.remove(key);
					//System.out.println("recycling pos=" + key);				
				}
			}
		}
	}

	private void appendMemo2(Memo2 m, Memo2 n) {
		while(m.next != null) {
			m = m.next;
		}
		m.next = n;
	}
	
	private Memo2 UnusedMemo = null;
	
	private void cachePreCheck(Peg keypeg, long keypos, Peg peg, long pos) {
		Memo2 m = null;
		if(UnusedMemo != null) {
			m = this.UnusedMemo;
			this.UnusedMemo = m.next;
		}
		else {
			m = new Memo2();
			this.memoSize += 1;
		}
		m.keypeg = keypeg;
		m.pos = pos;
		m.createdPeg = peg;
		m.next = this.memoMap2.get(keypos);
		this.memoMap2.put(keypos, m);
		this.memoMiss += 1;
//		if(keypeg == peg) {
//			System.out.println("cache " + keypos + ", " + keypeg);
//		}
	}
	
	public PegObject precheck(PegNewObject keypeg, PegObject inNode) {
		if(this.memoMap2 != null) { 
			long keypos = this.getPosition();
			Memo2 m = this.getPreCheckCache(keypeg, keypos);
			//System.out.println("cache? " + keypos + ", " + keypeg + ", m=" + m );
			boolean verifyMode = this.startVerifyMode();
			if(m != null) {
				this.memoHit += 1;
				this.endVerifyMode(verifyMode);
				if(m.createdPeg == keypeg) {
					this.setPosition(m.pos);  // comsume
					if(verifyMode) {
						return inNode;
					}
					//this.removePreCheckCache(keypos, m.pos);
					return null;
				}
				return this.refoundFailure(m.createdPeg, m.pos);
			}
			PegObject vnode = inNode;
			for(int i = 0; i < keypeg.size(); i++) {
				Peg e = keypeg.get(i);
				vnode = e.performMatch(vnode, this);
				if(vnode.isFailure()) {
					this.endVerifyMode(verifyMode);
					this.rollback(keypos);
					this.cachePreCheck(keypeg, keypos, vnode.createdPeg, vnode.startIndex);
					return vnode;
				}
			}
			this.endVerifyMode(verifyMode);
			if(verifyMode) {
				this.cachePreCheck(keypeg, keypos, keypeg, this.getPosition());
				return vnode;
			}
			//this.removePreCheckCache(keypos, this.getPosition());
			//this.rollback(keypos);
		}
		return null;
	}
	
	private boolean verifyMode = false;
	public final boolean isVerifyMode() {
		return this.verifyMode;
	}
	public final boolean startVerifyMode() {
		boolean verifyMode = this.verifyMode;
		this.verifyMode = true;
		return verifyMode;
	}
	public final void endVerifyMode(boolean verifyMode) {
		this.verifyMode = verifyMode;
	}
	
	public final int getStackPosition(Peg trace) {
		this.pushImpl(trace, null, '\0', null, 0, null);
		return this.stackTop;
	}

	private void pushImpl(Peg trace, String msg, char type, PegObject parentNode, int index, PegObject childNode) {
		SimpleLog log = null;
		if(this.stackTop < this.logStack.size()) {
			if(this.logStack.ArrayValues[this.stackTop] == null) {
				this.logStack.ArrayValues[this.stackTop] = new SimpleLog();
			}
			log = this.logStack.ArrayValues[this.stackTop];
		}
		else {
			log = new SimpleLog();
			this.logStack.add(log);
		}
		log.trace = trace;
		log.sourcePosition = this.sourcePosition;
		log.type = type;
		log.parentNode = parentNode;
		log.index = index;
		log.childNode = childNode;
		this.stackTop = this.stackTop + 1;
	}

	void pushLog(Peg trace, String msg) {
		this.pushImpl(trace, msg, 'm', null, 0, null);
	}

	@Override
	public void addSubObject(PegObject newnode, int stack, int top) {
		for(int i = stack; i < top; i++) {
			SimpleLog log = this.logStack.ArrayValues[i];
			if(log.type == 'p' && log.parentNode == newnode) {
				if(log.index == -1) {
					newnode.append(log.childNode);
				}
				else {
					newnode.set(log.index, log.childNode);
				}
			}
		}
		this.popBack(stack, false);
	}

	public void popBack(int stackPostion, boolean backtrack) {
		this.stackTop = stackPostion-1;
		SimpleLog log = this.logStack.ArrayValues[stackPostion-1];
		if(backtrack) {
			this.rollback(log.sourcePosition);
		}
	}

	public void push(Peg trace, PegObject parentNode, int index, PegObject node) {
		this.pushImpl(trace, "", 'p', parentNode, index, node);
	}
	
	public void showStatInfo(PegObject parsedObject) {
		System.gc(); // meaningless ?
		System.out.println("hit: " + this.memoHit + ", miss: " + this.memoMiss + ", consumed memo:" + this.memoSize);
		System.out.println("created_object: " + this.objectCount + ", used_object: " + parsedObject.count());
		System.out.println("backtrackCount: " + this.backtrackCount + ", backtrackLength: " + this.backtrackSize);
		long length = this.source.length();
		System.out.println("length: " + this.source.length() + ", length/backtrackLength: " + (double)length / this.backtrackSize);
		long total = Runtime.getRuntime().totalMemory() / 1024;
		long free =  Runtime.getRuntime().freeMemory() / 1024;
		long used =  total - free;
		System.out.println("heap: " + used + "KiB, " + (used/1024) + "MiB,  heap/length: " + (double) used / length * 1024);
		System.out.println();
	}

}