package de.thm.mni.compilerbau.phases._04a_tablebuild;

import de.thm.mni.compilerbau.absyn.*;
import de.thm.mni.compilerbau.absyn.visitor.DoNothingVisitor;
import de.thm.mni.compilerbau.table.*;
import de.thm.mni.compilerbau.types.ArrayType;
import de.thm.mni.compilerbau.types.Type;
import de.thm.mni.compilerbau.utils.NotImplemented;
import de.thm.mni.compilerbau.utils.SplError;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class is used to create and populate a {@link SymbolTable} containing entries for every symbol in the currently
 * compiled SPL program.
 * Every declaration of the SPL program needs its corresponding entry in the {@link SymbolTable}.
 * <p>
 * Calculated {@link Type}s can be stored in and read from the dataType field of the {@link Expression},
 * {@link TypeExpression} or {@link Variable} classes.
 */
public class TableBuilder {
    private final boolean showTables;
    private SymbolTable globalTable;
    private SymbolTable localTable;

    public TableBuilder(boolean showTables) {
        this.showTables = showTables;
    }

    public class MyVisitor extends DoNothingVisitor {
        public void visit(Program program) {
            program.declarations.forEach(d -> d.accept(this));
        }

        @Override
        public void visit(ArrayTypeExpression ate) {
            ate.baseType.accept(this);
            ate.dataType = new ArrayType(ate.baseType.dataType, ate.arraySize);
        }

        @Override
        public void visit(NamedTypeExpression nte) {
            var checkEntry = globalTable.lookup(nte.name, SplError.UndefinedType(nte.position, nte.name));
            if (checkEntry instanceof TypeEntry) {
                nte.dataType = ((TypeEntry) checkEntry).type;
            } else {
                throw SplError.NotAType(nte.position, nte.name);
            }
        }

        @Override
        public void visit(ParameterDeclaration pad) {
            pad.typeExpression.accept(this);
            if (pad.typeExpression.dataType instanceof ArrayType && !pad.isReference) {
                throw SplError.MustBeAReferenceParameter(pad.position, pad.name);
            } else {
                localTable.enter(pad.name, new VariableEntry(pad.typeExpression.dataType, pad.isReference),
                        SplError.RedeclarationAsParameter(pad.position, pad.name));
            }
        }

        @Override
        public void visit(ProcedureDeclaration prd) {
            List <ParameterType> pT = new ArrayList<>();
            localTable = new SymbolTable(globalTable);
            ProcedureEntry pE = new ProcedureEntry(localTable, pT);
            prd.parameters.forEach(p -> {
                p.accept(this);
                pE.parameterTypes.add(new ParameterType(p.typeExpression.dataType, p.isReference));
            });
            prd.variables.forEach(v -> v.accept(this));
            printSymbolTableAtEndOfProcedure(prd.name, pE);
            globalTable.enter(prd.name, pE, SplError.RedeclarationAsProcedure(prd.position, prd.name));
        }

        @Override
        public void visit(TypeDeclaration td) {
            td.typeExpression.accept(this);
            globalTable.enter(td.name, new TypeEntry(td.typeExpression.dataType), SplError.RedeclarationAsType(td.position, td.name));
        }

        @Override
        public void visit(VariableDeclaration vd) {
            vd.typeExpression.accept(this);
            localTable.enter(vd.name, new VariableEntry(vd.typeExpression.dataType, false), SplError.RedeclarationAsVariable(vd.position, vd.name));
        }

    }

    public SymbolTable buildSymbolTable(Program program) {
        //TODO (assignment 4a): Initialize a symbol table with all predefined symbols and fill it with user-defined symbols
        globalTable = TableInitializer.initializeGlobalTable();

        MyVisitor visitor = new MyVisitor();
        program.accept(visitor);
        //visitor.visit(program);

        var checkMain = globalTable.lookup(new Identifier("main"), SplError.MainIsMissing());

        if (!(checkMain instanceof ProcedureEntry)) {
            throw SplError.MainIsNotAProcedure();
        } else {
            if (!((ProcedureEntry) checkMain).parameterTypes.isEmpty()) {
                throw SplError.MainMustNotHaveParameters();
            }
        }

        return globalTable;
    }


    /**
     * Prints the local symbol table of a procedure together with a heading-line
     * NOTE: You have to call this after completing the local table to support '--tables'.
     *
     * @param name  The name of the procedure
     * @param entry The entry of the procedure to print
     */
    private static void printSymbolTableAtEndOfProcedure(Identifier name, ProcedureEntry entry) {
        System.out.format("Symbol table at end of procedure '%s':\n", name);
        System.out.println(entry.localTable.toString());
    }
}
