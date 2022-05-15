package de.thm.mni.compilerbau.absyn;

import de.thm.mni.compilerbau.absyn.visitor.Visitor;

public class DoWhileStatement extends Statement {
    public final Expression condition;
    public final Statement body;

    public DoWhileStatement(Position position, Expression condition, Statement body) {
        super(position);
        this.condition = condition;
        this.body = body;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return formatAst("DoWhileStatement", condition, body);
    }
}
