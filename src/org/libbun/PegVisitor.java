package org.libbun;

public interface PegVisitor {
	public void visitPegString(PegString peg);
	public void visitPegAny(PegAny peg);
	public void visitPegCharacter(PegCharacter peg);
	public void visitPegLabel(PegLabel peg);
	public void visitPegOptional(PegOptional peg);
	public void visitOneMore(PegOneMore peg);
	public void visitZeroMore(PegZeroMore peg);
	public void visitPegAnd(PegAndPredicate peg);
	public void visitPegNot(PegNotPredicate peg);
	public void visitSequence(PegSequence peg);
	public void visitChoice(PegChoice peg);
	public void visitSetter(PegSetter peg);
	public void visitNewObject(PegNewObject peg);
	public void visitObjectLabel(PegObjectLabel peg);
	public void visitCatch(PegCatch peg);
	public void visitIndent(PegIndent peg);
}
