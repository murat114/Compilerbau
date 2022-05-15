package de.thm.mni.compilerbau.phases._05_varalloc;

import de.thm.mni.compilerbau.absyn.*;
import de.thm.mni.compilerbau.absyn.visitor.DoNothingVisitor;
import de.thm.mni.compilerbau.table.ParameterType;
import de.thm.mni.compilerbau.table.ProcedureEntry;
import de.thm.mni.compilerbau.table.SymbolTable;
import de.thm.mni.compilerbau.table.VariableEntry;
import de.thm.mni.compilerbau.utils.*;

import java.util.AbstractMap;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * This class is used to calculate the memory needed for variables and stack frames of the currently compiled SPL program.
 * Those value have to be stored in their corresponding fields in the {@link ProcedureEntry}, {@link VariableEntry} and
 * {@link ParameterType} classes.
 */
public class VarAllocator {
    public static final int REFERENCE_BYTESIZE = 4;

    private final boolean showVarAlloc;
    private final boolean ershovOptimization;


    class MyVisitor extends DoNothingVisitor {
        private SymbolTable globalTable;
        int tmpOutgoing = -1;


        public MyVisitor(SymbolTable gT){
            globalTable = gT;
        }

        @Override
        public void visit(Program program) {
            program.declarations.forEach(d -> d.accept(this));
        }

        @Override
        public void visit(ProcedureDeclaration pD) {
            var entry = (ProcedureEntry) globalTable.lookup(pD.name);
            entry.stackLayout.localVarAreaSize = 0;
            entry.stackLayout.outgoingAreaSize = -1;
            entry.stackLayout.argumentAreaSize = pD.parameters.size() * 4;

            int tmp = 0;

            for (int i = 0; i < pD.variables.size(); i++) {
                entry.stackLayout.localVarAreaSize += pD.variables.get(i).typeExpression.dataType.byteSize;
                VariableEntry vE = (VariableEntry) entry.localTable.lookup(pD.variables.get(i).name);
                vE.offset = tmp - pD.variables.get(i).typeExpression.dataType.byteSize;
                tmp = vE.offset;
            }

            for (int i = 0; i < pD.parameters.size(); i++){
                entry.parameterTypes.get(i).offset = i * 4;
                VariableEntry vE = (VariableEntry) entry.localTable.lookup(pD.parameters.get(i).name);
                vE.offset = i * 4;
            }

            pD.body.forEach(b -> b.accept(this));
            entry.stackLayout.outgoingAreaSize = tmpOutgoing;
        }

        @Override
        public void visit(CompoundStatement coS){
            for (int i = 0; i < coS.statements.size(); i++){
                coS.statements.get(i).accept(this);
            }
        }

        @Override
        public void visit(CallStatement cS) {
            ProcedureEntry entry = (ProcedureEntry)globalTable.lookup(cS.procedureName);
            tmpOutgoing = Math.max(entry.parameterTypes.size() * 4, tmpOutgoing);
        }

        @Override
        public void visit(IfStatement ifS){
            ifS.elsePart.accept(this);
            ifS.thenPart.accept(this);
        }

        @Override
        public void visit(WhileStatement wS){
            wS.body.accept(this);
            wS.condition.accept(this);
        }

        @Override
        public void visit(DoWhileStatement dWS){
            dWS.body.accept(this);
            dWS.condition.accept(this);
        }
    }


    /**
     * @param showVarAlloc       Whether to show the results of the variable allocation after it is finished
     * @param ershovOptimization Whether the ershov register optimization should be used (--ershov)
     */
    public VarAllocator(boolean showVarAlloc, boolean ershovOptimization) {
        this.showVarAlloc = showVarAlloc;
        this.ershovOptimization = ershovOptimization;
    }

    public void allocVars(Program program, SymbolTable table) {
        //TODO (assignment 5): Allocate stack slots for all parameters and local variables
        MyVisitor visitor = new MyVisitor(table);
        program.accept(visitor);



        //TODO: Uncomment this when the above exception is removed!
        if (showVarAlloc)
            formatVars(program, table);
    }


    /**
     * Formats and prints the variable allocation to a human-readable format
     * The stack layout
     *
     * @param program The abstract syntax tree of the program
     * @param table   The symbol table containing all symbols of the spl program
     */
    private void formatVars(Program program, SymbolTable table) {
        program.declarations.stream().filter(dec -> dec instanceof ProcedureDeclaration).map(dec -> (ProcedureDeclaration) dec).forEach(procDec -> {
            ProcedureEntry entry = (ProcedureEntry) table.lookup(procDec.name);

            AsciiGraphicalTableBuilder ascii = new AsciiGraphicalTableBuilder();
            ascii.line("...", AsciiGraphicalTableBuilder.Alignment.CENTER);

            {
                final var zipped = IntStream.range(0, procDec.parameters.size()).boxed()
                        .map(i -> new Pair<>(procDec.parameters.get(i), new Pair<>(((VariableEntry) entry.localTable.lookup(procDec.parameters.get(i).name)), entry.parameterTypes.get(i))))
                        .sorted(Comparator.comparing(p -> Optional.ofNullable(p.second.first.offset).map(o -> -o).orElse(Integer.MIN_VALUE)));

                zipped.forEach(v -> {
                    boolean consistent = Objects.equals(v.second.first.offset, v.second.second.offset);

                    ascii.line("par " + v.first.name.toString(), "<- FP + " +
                                    (consistent ?
                                            StringOps.toString(v.second.first.offset) :
                                            String.format("INCONSISTENT(%s/%s)",
                                                    StringOps.toString(v.second.first.offset),
                                                    StringOps.toString(v.second.second.offset))),
                            AsciiGraphicalTableBuilder.Alignment.LEFT);
                });
            }

            ascii.sep("BEGIN", "<- FP");
            if (!procDec.variables.isEmpty()) {
                procDec.variables.stream()
                        .map(v -> new AbstractMap.SimpleImmutableEntry<>(v, ((VariableEntry) entry.localTable.lookup(v.name))))
                        .sorted(Comparator.comparing(e -> Try.execute(() -> -e.getValue().offset).getOrElse(0)))
                        .forEach(v -> ascii.line("var " + v.getKey().name.toString(),
                                "<- FP - " + Optional.ofNullable(v.getValue().offset).map(o -> -o).map(StringOps::toString).orElse("NULL"),
                                AsciiGraphicalTableBuilder.Alignment.LEFT));

                ascii.sep("");
            }

            ascii.line("Old FP",
                    "<- SP + " + Try.execute(entry.stackLayout::oldFramePointerOffset).map(Objects::toString).getOrElse("UNKNOWN"),
                    AsciiGraphicalTableBuilder.Alignment.LEFT);

            if (Try.execute(entry.stackLayout::isLeafProcedure).getOrElse(false)) ascii.close("END", "<- SP");
            else {
                ascii.line("Old Return",
                        "<- FP - " + Try.execute(() -> -entry.stackLayout.oldReturnAddressOffset()).map(Objects::toString).getOrElse("UNKNOWN"),
                        AsciiGraphicalTableBuilder.Alignment.LEFT);

                if (entry.stackLayout.outgoingAreaSize == null || entry.stackLayout.outgoingAreaSize > 0) {

                    ascii.sep("outgoing area");

                    if (entry.stackLayout.outgoingAreaSize != null) {
                        var max_args = entry.stackLayout.outgoingAreaSize / 4;

                        for (int i = 0; i < max_args; ++i) {
                            ascii.line(String.format("arg %d", max_args - i),
                                    String.format("<- SP + %d", (max_args - i - 1) * 4),
                                    AsciiGraphicalTableBuilder.Alignment.LEFT);
                        }
                    } else {
                        ascii.line("UNKNOWN SIZE", AsciiGraphicalTableBuilder.Alignment.LEFT);
                    }
                }

                ascii.sep("END", "<- SP");
                ascii.line("...", AsciiGraphicalTableBuilder.Alignment.CENTER);
            }

            System.out.printf("Variable allocation for procedure '%s':\n", procDec.name);
            System.out.printf("  - size of argument area = %s\n", StringOps.toString(entry.stackLayout.argumentAreaSize));
            System.out.printf("  - size of localvar area = %s\n", StringOps.toString(entry.stackLayout.localVarAreaSize));
            System.out.printf("  - size of outgoing area = %s\n", StringOps.toString(entry.stackLayout.outgoingAreaSize));
            System.out.printf("  - frame size = %s\n", Try.execute(entry.stackLayout::frameSize).map(Objects::toString).getOrElse("UNKNOWN"));
            System.out.println();
            System.out.println("  Stack layout:");
            System.out.println(StringOps.indent(ascii.toString(), 4));
            System.out.println();
        });
    }
}
