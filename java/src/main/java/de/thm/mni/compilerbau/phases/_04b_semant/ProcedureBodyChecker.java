package de.thm.mni.compilerbau.phases._04b_semant;

import de.thm.mni.compilerbau.absyn.*;
import de.thm.mni.compilerbau.absyn.visitor.DoNothingVisitor;
import de.thm.mni.compilerbau.table.Entry;
import de.thm.mni.compilerbau.table.ProcedureEntry;
import de.thm.mni.compilerbau.table.SymbolTable;
import de.thm.mni.compilerbau.table.VariableEntry;
import de.thm.mni.compilerbau.types.ArrayType;
import de.thm.mni.compilerbau.types.PrimitiveType;
import de.thm.mni.compilerbau.types.Type;
import de.thm.mni.compilerbau.utils.NotImplemented;
import de.thm.mni.compilerbau.utils.SplError;

import java.lang.reflect.Array;

/**
 * This class is used to check if the currently compiled SPL program is semantically valid.
 * The body of each procedure has to be checked, consisting of {@link Statement}s, {@link Variable}s and {@link Expression}s.
 * Each node has to be checked for type issues or other semantic issues.
 * Calculated {@link Type}s can be stored in and read from the dataType field of the {@link Expression} and {@link Variable} classes.
 */
public class ProcedureBodyChecker {

    private SymbolTable globalTable;
    private SymbolTable localTable;

    class MyVisitor extends DoNothingVisitor{



        @Override
        public void visit(Program program){
            program.declarations.forEach(d -> d.accept(this));
        }

        @Override
        public void visit(ProcedureDeclaration pD){
            ProcedureEntry pE = (ProcedureEntry)globalTable.lookup(pD.name);
            localTable = pE.localTable;
            pD.body.forEach(x -> x.accept(this));
        }

        @Override
        public void visit(AssignStatement aS) {
            aS.target.accept(this);
            aS.value.accept(this);

            // value und target müssen den gleichen Typ haben
            if(aS.value.dataType != aS.target.dataType){
                throw SplError.AssignmentHasDifferentTypes(aS.position);
            }
            if (aS.target.dataType != PrimitiveType.intType){
                throw SplError.AssignmentRequiresIntegers(aS.position);
            }
        }

        @Override
        public void visit(BinaryExpression bE) {
            bE.leftOperand.accept(this);
            bE.rightOperand.accept(this);

            // Operanden müssen den gleichen Typ haben
            if(bE.leftOperand.dataType != bE.rightOperand.dataType){
                throw SplError.OperatorDifferentTypes(bE.position);
            }
            if (!(bE.operator.isArithmetic())){
                if((bE.leftOperand.dataType == PrimitiveType.boolType) && (bE.rightOperand.dataType == PrimitiveType.boolType)){
                    throw SplError.ComparisonNonInteger(bE.position);
                }
                bE.dataType = PrimitiveType.boolType;
            } else {
                if((bE.leftOperand.dataType == PrimitiveType.boolType) && (bE.rightOperand.dataType == PrimitiveType.boolType)){
                    throw SplError.ArithmeticOperatorNonInteger(bE.position);
                }
                bE.dataType = PrimitiveType.intType;
            }
        }

        @Override
        public void visit(IfStatement ifS) {
            ifS.condition.accept(this);
            ifS.elsePart.accept(this);
            ifS.thenPart.accept(this);

            // Bedingung muss Bool sein
            if (ifS.condition.dataType != PrimitiveType.boolType){
                throw SplError.IfConditionMustBeBoolean(ifS.position);
            }
        }

        @Override
        public void visit(IntLiteral intL) {
            intL.dataType = PrimitiveType.intType;
        }

        @Override
        public void visit(CompoundStatement cS) {
            cS.statements.forEach(states -> states.accept(this));
        }

        @Override
        public void visit(EmptyStatement eS) {

        }

        @Override
        public void visit(WhileStatement whileS) {
            whileS.condition.accept(this);
            whileS.body.accept(this);

            // Bedingung muss Bool sein
            if(whileS.condition.dataType != PrimitiveType.boolType){
                throw SplError.WhileConditionMustBeBoolean(whileS.position);
            }
        }

        @Override
        public void visit(DoWhileStatement dWS) {
            dWS.body.accept(this);
            dWS.condition.accept(this);

            // Bedingung muss Bool sein
            if(dWS.condition.dataType != PrimitiveType.boolType){
                throw SplError.DoWhileConditionMustBeBoolean(dWS.position);
            }
        }

        @Override
        public void visit(VariableExpression vE) {
            vE.variable.accept(this);
            vE.dataType = vE.variable.dataType;
        }

        @Override
        public void visit(NamedVariable nV){
            Entry entry = localTable.lookup(nV.name, SplError.UndefinedVariable(nV.position, nV.name));
            if (!(entry instanceof VariableEntry)){
                throw SplError.NotAVariable(nV.position, nV.name);
            } else {
                nV.dataType = ((VariableEntry) entry).type;
            }
        }

        @Override
        public void visit(ArrayAccess aA) {
            aA.array.accept(this);
            aA.index.accept(this);

            // index muss int sein
            if(aA.index.dataType != PrimitiveType.intType){
                throw SplError.IndexingWithNonInteger(aA.position);
            }
            // Datentypen müssen korrekt sein
            if (!(aA.array.dataType instanceof ArrayType)){
                throw SplError.IndexingNonArray(aA.position);
            }
            ArrayType aT = (ArrayType) aA.array.dataType;
            aA.dataType = aT.baseType;
        }

        @Override
        public void visit(CallStatement cS){
            cS.arguments.forEach(args -> args.accept(this));
            Entry entry = globalTable.lookup(cS.procedureName, SplError.UndefinedProcedure(cS.position, cS.procedureName));
            if (!(entry instanceof ProcedureEntry)){
                throw SplError.CallOfNonProcedure(cS.position, cS.procedureName);
            }
            ProcedureEntry pE = (ProcedureEntry)entry;
            if (cS.arguments.size() > pE.parameterTypes.size()){
                throw SplError.TooManyArguments(cS.position, cS.procedureName);
            }
            if (cS.arguments.size() < pE.parameterTypes.size()){
                throw SplError.TooFewArguments(cS.position, cS.procedureName);
            }
            for(int i = 0; i < cS.arguments.size(); i++){
                if (cS.arguments.get(i).dataType != pE.parameterTypes.get(i).type){
                    throw SplError.ArgumentTypeMismatch(cS.position, cS.procedureName, i+1);
                }
                if (!(cS.arguments.get(i) instanceof VariableExpression) && (pE.parameterTypes.get(i).isReference)){
                    throw SplError.ArgumentMustBeAVariable(cS.position, cS.procedureName, i+1);
                }
            }
        }



    }

    public void checkProcedures(Program program, SymbolTable globalTable) {
        //TODO (assignment 4b): Check all procedure bodies for semantic errors
        this.globalTable = globalTable;
        MyVisitor visitor = new MyVisitor();
        program.accept(visitor);
    }
}
