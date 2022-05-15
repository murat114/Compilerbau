package de.thm.mni.compilerbau.phases._01_scanner;

import de.thm.mni.compilerbau.utils.SplError;
import de.thm.mni.compilerbau.phases._02_03_parser.Sym;
import de.thm.mni.compilerbau.absyn.Position;
import de.thm.mni.compilerbau.table.Identifier;
import de.thm.mni.compilerbau.CommandLineOptions;
import java_cup.runtime.*;

%%


%class Scanner
%public
%line
%column
%cup
%eofval{
    return new java_cup.runtime.Symbol(Sym.EOF, yyline + 1, yycolumn + 1);   //This needs to be specified when using a custom sym class name
%eofval}

%{
    public CommandLineOptions options = null;
  
    private Symbol symbol(int type) {
      return new Symbol(type, yyline + 1, yycolumn + 1);
    }

    private Symbol symbol(int type, Object value) {
      return new Symbol(type, yyline + 1, yycolumn + 1, value);
    }
%}


//numbers = [0-9]+
identifier = [A-Za-z_$][A-Za-z_$0-9]*
comments = \/\/.*
characters = '.'
hexa = 0x[0-9a-fA-F]+
/*
L       = [A-Za-z_]
D       = [0-9]
H       = [0-9A-Fa-f]
ID      = {L}({L}|{D})*
DECNUM  = {D}+
HEXNUM  = 0x{H}+
 */

ASCII = '\\n'

%%

[ \t\n\r] {}

"\("   {return symbol(Sym.LPAREN);}
"\)"   {return symbol(Sym.RPAREN);}
"\{"   {return symbol(Sym.LCURL);}
"\}"   {return symbol(Sym.RCURL);}
"["   {return symbol(Sym.LBRACK);}
"]"   {return symbol(Sym.RBRACK);}

"<"   {return symbol(Sym.LT);}
"<="  {return symbol(Sym.LE);}
">"   {return symbol(Sym.GT);}
">="  {return symbol(Sym.GE);}
"#"   {return symbol(Sym.NE);}
"="   {return symbol(Sym.EQ);}

"\+"   {return symbol(Sym.PLUS);}
"\-"   {return symbol(Sym.MINUS);}
"\*"   {return symbol(Sym.STAR);}
"\/"  {return symbol(Sym.SLASH);}

":="  {return symbol(Sym.ASGN);}
","   {return symbol(Sym.COMMA);}
":"   {return symbol(Sym.COLON);}
";"   {return symbol(Sym.SEMIC);}

type   {return symbol(Sym.TYPE);}
proc   {return symbol(Sym.PROC);}
array  {return symbol(Sym.ARRAY);}
of     {return symbol(Sym.OF);}
ref    {return symbol(Sym.REF);}
var    {return symbol(Sym.VAR);}
if     {return symbol(Sym.IF);}
else   {return symbol(Sym.ELSE);}
while  {return symbol(Sym.WHILE);}
do     {return options.doWhileEnabled ? symbol(Sym.DO) : symbol(Sym.IDENT, new Identifier(yytext()));}
eof    {return symbol(Sym.EOF);}
ref    {return symbol(Sym.REF);}


{comments} {/*nothing todo here*/}
[0-9]+ {return symbol(Sym.INTLIT, Integer.parseInt(yytext()));}
{identifier} {return symbol(Sym.IDENT, new Identifier(yytext()));}
{characters} {return symbol(Sym.INTLIT, (int)yytext().charAt(1));}
{hexa} {return symbol (Sym.INTLIT, Integer.decode(yytext()));}
{ASCII}   {return symbol(Sym.INTLIT, 10);}






[^]		{throw SplError.IllegalCharacter(new Position(yyline + 1, yycolumn + 1), yytext().charAt(0));}
