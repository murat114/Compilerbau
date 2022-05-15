package de.thm.mni.compilerbau.phases._06_codegen;

import de.thm.mni.compilerbau.absyn.*;
import de.thm.mni.compilerbau.absyn.visitor.DoNothingVisitor;
import de.thm.mni.compilerbau.phases._02_03_parser.Sym;
import de.thm.mni.compilerbau.phases._05_varalloc.VarAllocator;
import de.thm.mni.compilerbau.table.ProcedureEntry;
import de.thm.mni.compilerbau.table.SymbolTable;
import de.thm.mni.compilerbau.table.VariableEntry;
import de.thm.mni.compilerbau.types.ArrayType;
import de.thm.mni.compilerbau.utils.NotImplemented;
import de.thm.mni.compilerbau.utils.SplError;

import java.awt.*;
import java.io.PrintWriter;

/**
 * This class is used to generate the assembly code for the compiled program.
 * This code is emitted via the {@link CodePrinter} in the output field of this class.
 */
public class CodeGenerator {
    private final CodePrinter output;
    private final boolean ershovOptimization;
    private String label;

    /**
     * Initializes the code generator.
     *
     * @param output             The PrintWriter to the output file.
     * @param ershovOptimization Whether the ershov register optimization should be used (--ershov)
     */
    public CodeGenerator(PrintWriter output, boolean ershovOptimization) {
        this.output = new CodePrinter(output);
        this.ershovOptimization = ershovOptimization;
    }

    /**
     * Emits needed import statements, to allow usage of the predefined functions and sets the correct settings
     * for the assembler.
     */
    private void assemblerProlog() {
        output.emitImport("printi");
        output.emitImport("printc");
        output.emitImport("readi");
        output.emitImport("readc");
        output.emitImport("exit");
        output.emitImport("time");
        output.emitImport("clearAll");
        output.emitImport("setPixel");
        output.emitImport("drawLine");
        output.emitImport("drawCircle");
        output.emitImport("_indexError");
        output.emit("");
        output.emit("\t.code");
        output.emit("\t.align\t4");
    }

    class MyVisitor extends DoNothingVisitor{

        Register zeroRegister = new Register(0);
        Register tmpRegister = new Register(8);
        Register framePointerRegister = new Register(25);
        Register stackPointerRegister = new Register(29);
        Register returnAddressRegister = new Register(31);

        private SymbolTable globaltable;
        private SymbolTable localtable;
        private CodePrinter output;
        private int labelCounter = 0;

        public MyVisitor(SymbolTable globalTable, CodePrinter output){
            globaltable = globalTable;
            this.output = output;
        }

        @Override
        public void visit(Program program){
            program.declarations.forEach(d -> d.accept(this));
        }

        @Override
        public void visit(ProcedureDeclaration pD){
            var entry = (ProcedureEntry)globaltable.lookup(pD.name);
            localtable = entry.localTable;
            // Framegröße berechnen
            int frameSize = entry.stackLayout.frameSize();
            // Prozedur-Prolog ausgeben
            output.emitExport(pD.name.toString());
            output.emitLabel(pD.name.toString());
            output.emitInstruction("sub", stackPointerRegister, stackPointerRegister, frameSize,"allocate SP");
            output.emitInstruction("stw", framePointerRegister, stackPointerRegister, entry.stackLayout.oldFramePointerOffset(),"allocate FP");
            output.emitInstruction("add", framePointerRegister, stackPointerRegister, frameSize,"FP -> SP + FrameSize");
            if (!(entry.stackLayout.isLeafProcedure())){
                output.emitInstruction("stw", returnAddressRegister, framePointerRegister, entry.stackLayout.oldReturnAddressOffset(),"allocate ReturnAdr");
            }
            // Code für Prozedurkörper erzeugen
            pD.body.forEach(b -> b.accept(this));
            // Prozedur-Epilog ausgeben
            if (!(entry.stackLayout.isLeafProcedure())) {
                output.emitInstruction("ldw", returnAddressRegister, framePointerRegister, entry.stackLayout.oldReturnAddressOffset(), "restore return register");
            }
            output.emitInstruction("ldw", framePointerRegister, stackPointerRegister, entry.stackLayout.oldFramePointerOffset(),"restore FP");
            output.emitInstruction("add", stackPointerRegister, stackPointerRegister, entry.stackLayout.frameSize(),"release frame");
            output.emitInstruction("jr", returnAddressRegister, "return");
        }


        @Override
        public void visit(CallStatement cS){
            var entry = (ProcedureEntry)globaltable.lookup(cS.procedureName);
            for (int i = 0; i < cS.arguments.size(); i ++){
                if (!(entry.parameterTypes.get(i).isReference)){
                    cS.arguments.get(i).accept(this);
                } else {
                    ((VariableExpression)cS.arguments.get(i)).variable.accept(this);
                }
                output.emitInstruction("stw", tmpRegister.previous(), stackPointerRegister, entry.parameterTypes.get(i).offset, "store arg #" + i);
                tmpRegister = tmpRegister.previous();
            }
            output.emitInstruction("jal", cS.procedureName.toString());
        }

        @Override
        public void visit(CompoundStatement compS){
            compS.statements.forEach(s -> s.accept(this));
        }

        @Override
        public void visit(IfStatement iS){
            //tmpRegister = new Register(8);
            int exitLabel = labelCounter++;
            if (iS.elsePart instanceof EmptyStatement){
                logicalBinaryExpression((BinaryExpression)iS.condition, "L" + exitLabel);
                iS.thenPart.accept(this);
                output.emitLabel("L" + exitLabel);
            } else {
                int elseLabel = exitLabel;
                exitLabel = labelCounter++;
                logicalBinaryExpression((BinaryExpression)iS.condition, "L" + elseLabel);
                iS.thenPart.accept(this);
                output.emitInstruction("j", "L" + exitLabel);
                output.emitLabel("L" + elseLabel);
                iS.elsePart.accept(this);
                output.emitLabel("L" + exitLabel);
            }
        }

        @Override
        public void visit(WhileStatement wS){
            //tmpRegister = new Register(8);
            int loopLabel = labelCounter ++;
            output.emitLabel("L" + loopLabel);
            int exitLabel = labelCounter ++;
            logicalBinaryExpression((BinaryExpression)wS.condition, "L" + exitLabel);
            wS.body.accept(this);
            output.emitInstruction("j", "L" + loopLabel);
            output.emitLabel("L" + exitLabel);
        }

        @Override
        public void visit(DoWhileStatement dWS){
            int localLabelCounter = labelCounter ++;
            output.emitLabel("L" + localLabelCounter);
            dWS.body.accept(this);
            logicalBinaryExpression((BinaryExpression)dWS.condition, "L" + localLabelCounter, false);
        }

        @Override
        public void visit(NamedVariable nV){
            VariableEntry entry = (VariableEntry)localtable.lookup(nV.name);
            output.emitInstruction("add", tmpRegister, framePointerRegister, entry.offset);
            if (entry.isReference){
                output.emitInstruction("ldw", tmpRegister, tmpRegister, 0);
            }
            tmpRegister = tmpRegister.next();
        }

        @Override
        public void visit(ArrayAccess aA){
            aA.array.accept(this);
            aA.index.accept(this);

            Register arrayRegister = tmpRegister.minus(2);
            Register indexRegister = tmpRegister.previous();
            Register localRegister = tmpRegister;

            output.emitInstruction("add", localRegister, zeroRegister, ((ArrayType)aA.array.dataType).arraySize);
            output.emitInstruction("bgeu", indexRegister, localRegister, "_indexError");
            output.emitInstruction("mul", indexRegister, indexRegister, ((ArrayType)aA.array.dataType).baseType.byteSize);
            output.emitInstruction("add", arrayRegister, arrayRegister, indexRegister);

            tmpRegister = tmpRegister.previous();
        }

        @Override
        public void visit(BinaryExpression bE) {
            bE.leftOperand.accept(this);
            Register leftRegister = tmpRegister.previous();
            bE.rightOperand.accept(this);
            Register rightRegister = tmpRegister.previous();
            var operator = bE.operator;

            switch (operator){
                case ADD:
                    output.emitInstruction("add", leftRegister, leftRegister, rightRegister);
                    break;
                case SUB:
                    output.emitInstruction("sub", leftRegister, leftRegister, rightRegister);
                    break;
                case MUL:
                    output.emitInstruction("mul", leftRegister, leftRegister, rightRegister);
                    break;
                case DIV:
                    output.emitInstruction("div", leftRegister, leftRegister, rightRegister);
                    break;
            }
            tmpRegister = tmpRegister.previous();
        }
        public void logicalBinaryExpression(BinaryExpression bE, String label){
            logicalBinaryExpression(bE, label, true);
        }
        public void logicalBinaryExpression(BinaryExpression bE, String label, boolean flip){
            bE.leftOperand.accept(this);
            Register leftRegister = tmpRegister.previous();
            bE.rightOperand.accept(this);
            Register rightRegister = tmpRegister.previous();
            var operator = bE.operator;

            if (flip){
                operator = operator.flipComparison();
            }
            switch (operator){
                case EQU:
                    output.emitInstruction("beq", leftRegister, rightRegister, label);
                    break;
                case NEQ:
                    output.emitInstruction("bne", leftRegister, rightRegister, label);
                    break;
                case GRE:
                    output.emitInstruction("bge", leftRegister, rightRegister, label);
                    break;
                case GRT:
                    output.emitInstruction("bgt", leftRegister, rightRegister, label);
                    break;
                case LST:
                    output.emitInstruction("blt", leftRegister, rightRegister, label);
                    break;
                case LSE:
                    output.emitInstruction("ble", leftRegister, rightRegister, label);
            }
            tmpRegister = tmpRegister.minus(2);
        }

        @Override
        public void visit(VariableExpression vE){
            vE.variable.accept(this);
            output.emitInstruction("ldw", tmpRegister.previous(), tmpRegister.previous(), 0);
        }

        @Override
        public void visit(IntLiteral intLit){
            output.emitInstruction("add", tmpRegister, zeroRegister, intLit.value);
            tmpRegister = tmpRegister.next();
        }

        @Override
        public void visit(AssignStatement aS){
            aS.target.accept(this);
            aS.value.accept(this);

            Register tmpRegisterMinusTwo = tmpRegister.minus(2);
            output.emitInstruction("stw", tmpRegister.previous(), tmpRegisterMinusTwo, 0, "assignStatement");
            tmpRegister = tmpRegister.minus(2);
        }
    }

    public void generateCode(Program program, SymbolTable table) {
        assemblerProlog();

        MyVisitor visitor = new MyVisitor(table, output);
        program.accept(visitor);
    }
}
