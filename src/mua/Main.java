package mua;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Stack;
import java.util.Vector;

abstract class Value implements Serializable {
	private static final long serialVersionUID = 0x3180101729000000L;
	static boolean isValue(String str) {	//check if str is a literal
		//System.err.println("Value.isValue(String): " + str);
		return MuaExpression.isExpression(str) ||
			   MuaList.isList(str) ||
			   MuaNumber.isNumber(str) ||
			   MuaWord.isWord(str) ||
			   MuaBoolean.isBoolean(str);
	}
	static Value toValue(String str) {
		//System.err.println("Value.toValue(String): " + str);
		if(MuaNumber.isNumber(str))
			return new MuaNumber(str);
		else if(MuaBoolean.isBoolean(str))
			return new MuaBoolean(str);
		else if(MuaWord.isWord(str))
			return new MuaWord(str.substring(1, str.length()));
		else
			return new MuaWord(str);	//word in list maybe no "
	}
	static Value toValue(String str, Scanner in) {	//change a literal to Value
		//System.err.println("Value.toValue(String, Scanner): " + str);
		if(MuaExpression.isExpression(str))
			return MuaExpression.calc(new StringStream(str, in));	//expression is not a value
		else if(MuaList.isList(str))
			return MuaList.toList(str, in);
		else
			return toValue(str);
	}
	static Value toValue(String str, StringStream stm) {
		//System.err.println("Value.toValue(String, StringStream): " + str + " - " + stm.toString() + "\tnow: " + stm.now);
		if(str.equals("["))
			return MuaList.getFirstList(stm.back());
		else if(str.equals("("))
			return MuaExpression.calc(stm.back());	//expression is not a value
		else
			return toValue(str);
	}
	static Value[] toArray(Value value) {
		Value[] ret;
		if(MuaList.isList(value))
			ret = MuaList.toValueArray(MuaList.toList(value));
		else {
			ret = new Value[1];
			ret[0] = value;
		}
		return ret;
	}
}

class StringStream implements Serializable {
	//change [[1 (2)]] to [ [ 1 ( 2 ) ] ]
	//split []()+-*/%
	//assume that all brackets are matched
	//use like Scanner
	private static final long serialVersionUID = 0x3180101729010000L;
	String[] str;
	int now;
	StringStream(String[] str) {
		this.str = str;
		this.now = 0;
	}
	StringStream(String str, Scanner in) {	//str[0]: the first bracket
		ArrayList<String> list = new ArrayList<String>();
		Stack<Character> brackets = new Stack<Character>();
		boolean flag = false;	//if s is used
		while(true) {
			int last = -1;	//pos of last not special char, alpha or digit
			for(int i = 0; i < str.length(); ++i) {
				char c = str.charAt(i);
				if(MuaExpression.isBracket(c) || MuaExpression.isOp(c)) {	//special char
					if(last != -1) {
						list.add(str.substring(last, i));
						last = -1;
					}
					if(c == '[' || c == '(')
						brackets.push(c);
					else if(c == ')' || c == ']')
						brackets.pop();
					if(c == '-' && (brackets.empty() || brackets.peek() == '['))	//[print add -1 -1]
						last = i;
					else
						list.add(str.substring(i, i + 1));
				}else if(last == -1)
					last = i;
			}
			if(last != -1)
				list.add(str.substring(last, str.length()));
			if(brackets.size() == 0)
				break;
			str = in.next();
		}
		//System.err.println();
		this.str = list.toArray(new String[0]);
		this.now = 0;
	}
	boolean isEmpty() { return str.length == 0; }
	boolean hasNext() { return now < str.length; }
	int length() { return str.length; }
	String next() { return str[now++]; }
	StringStream back() {
		--now;
		return this;
	}
	StringStream reset() {
		now = 0;
		return this;
	}
	public String toString() {
		if(str.length == 0) return "";
		String ret = str[0];
		for(int i = 1; i < str.length; ++i)
			ret = ret + " " + str[i];
		return ret;
	}
}

class MuaList extends Value {
	private static final long serialVersionUID = 0x3180101729001000L;
	StringStream value;
	MuaList() { this.value = null; }
	MuaList(StringStream value) {
		if(value == null) this.value = null;
		else {
			String[] s = value.str;
			this.value = new StringStream(Arrays.copyOfRange(s, 1, s.length - 1));
		}
	}
	MuaList(String[] value) {	//no [] begin-end
		this.value = new StringStream(value);
	}
	MuaList(Value[] values) {
		ArrayList<String> list = new ArrayList<String>();
		for(int i = 0; i < values.length; ++i)
			if(isList(values[i])) {
				list.add("[");
				list.addAll(Arrays.asList(toList(values[i]).getValue().str));	//NOT SO GOOD
				list.add("]");
			}else
				list.add(values[i].toString());
		this.value = new StringStream(list.toArray(new String[0]));
	}
	static MuaList toList(Value value) {
		if(value instanceof MuaList)
			return (MuaList)value;
		//TODO
		return null;
	}
	static MuaList toList(String str, Scanner in) {
		return new MuaList(new StringStream(str, in));
	}
	static MuaList getFirstList(StringStream stm) {	//extract the first list or report no list
		ArrayList<String> list = new ArrayList<String>();
		int top = 1;
		if(!stm.hasNext()) return null;
		String s = stm.next();
		if(!s.equals("[")) {
			stm.back();
			return null;
		}
		while(stm.hasNext()) {
			s = stm.next();
			if(s.equals("]"))
				--top;
			else if(s.equals("["))
				++top;
			if(top == 0) break;
			list.add(s);
		}
		return new MuaList(list.toArray(new String[0]));
	}
	static boolean isList(String str) {		//is a list literal
		return str.charAt(0) == '[';
	}
	static boolean isList(Value value) {	//is a list subclass
		if(value == null) return false;
		return value instanceof MuaList;
	}
	static boolean isFunction(Value value) {
		//System.err.println("MuaList.isFunction(Value)");
		if(!isList(value)) return false;
		StringStream stm = ((MuaList)value).getValue().reset();
		MuaList args = getFirstList(stm);
		MuaList ops  = getFirstList(stm);
		return args != null && ops != null && !stm.hasNext();
	}
	static Value[] toValueArray(MuaList list) {
		//System.err.println("MuaList.toValueArray: ");
		if(list.isEmpty())
			return null;
		StringStream stm = list.getValue().reset();
		String str;
		ArrayList<Value> arr = new ArrayList<Value>();
		Value val;
		while(stm.hasNext()) {
			str = stm.next();
			if(str.equals("["))
				val = getFirstList(stm.back());
			else
				val = new MuaWord(str);
			arr.add(val);
		}
		return arr.toArray(new Value[0]);
	}
	StringStream getValue() { return value; }
	boolean isEmpty() { return value == null || value.length() == 0; }
	public String toString() {
		String ret = "";//"[";
		Value[] values = toValueArray(this);
		for(int i = 0; i < values.length; ++i) {
			if(isList(values[i])) {
				ret += "[";
				ret += values[i].toString();
				ret += "]";
			}else
				ret += values[i].toString();
			if(i != values.length - 1)
				ret += " ";
		}
		return ret;// + "]";
	}
}

class MuaExpression {
	private static final long serialVersionUID = 0x3180101729020000L;
	static int opTop = 0;
    static Stack<Double> numStack = new Stack<Double>();
	static Stack<Character> opStack = new Stack<Character>();

	static boolean isExpression(String str) {	//is a expression literal
		return str.charAt(0) == '(';
	}

	static boolean isOp(char op) {
		return op == '+' || op == '-' || op == '*' || op == '/' || op == '%';
	}
	static boolean isBracket(char c) {
		return c == '(' || c == '[' || c == ']' || c == ')';
	}
	static double Calc(char op, double x, double y) {
		if(op == '+')
			return x + y;
		else if(op == '-')
			return x - y;
		else if(op == '*')
			return x * y;
		else if(op == '/')
			return x / y;
		else if(op == '%')
			return x - Math.floor(x / y) * y;
		else
			return 0;
	}
	static int opLevel(char c) {
		if(c == '*' || c == '/' || c == '%')
			return 1;
		return 0;
	}
	static void opPush(char c) {
		++opTop;
		opStack.push(c);
	}
	static char opPop() {
		--opTop;
		return opStack.pop();
	}
	static void Pop() {
		char op = opPop();
		double y = numStack.pop(), x = numStack.pop();
		numStack.push(Calc(op, x, y));
	}
	static Value calc(StringStream stm) {	//calc the first (expr)
		//System.err.println("Expression.calc(StringStream): " + stm.toString() + "\tnow: " + stm.now);
		int flag = 0;	//0: x no value
		int last = -1;
		int nowTop = opTop;
		while(stm.hasNext()) {
			String str = stm.next();
			char c = str.charAt(0);
			if(Character.isDigit(c)) {
				double x = Double.parseDouble(str);
				if(flag == -1) x = -x;
				flag = 0;
				numStack.push(x);
				last = 0;	//a number
			}else {
				if(isBracket(c)) {
					//if(c == '[')	//impossible
					if(c == '(') {
						opPush(c);
						last = 1;
					}else if(c == ')') {
						while(!opStack.empty() && opStack.peek() != '(')
							Pop();
						opPop();	//'(' match
						last = 0;	//bracket like number
					}
				}else if(Character.isAlphabetic(c) || c == ':') {	//operation
					Value tmp = Operation.Exec(stm.back());
					numStack.push(((MuaNumber)tmp).getValue());
					last = 0;
				}else if(isOp(c)) {
					if(last != 0) {	//not a number
						if(c == '-') {
							if(flag == 0) flag = -1;
							else flag *= -1;
						}//else if(c == '+');
					}else {
						while(!opStack.empty() && opStack.peek() != '(' && opLevel(c) <= opLevel(opStack.peek()))
							Pop();
						opPush(c);
					}
					last = 1;
				}
			}
			if(nowTop == opTop) break;	//calc first (expr)
		}
		while(nowTop != opTop)
			Pop();
		return new MuaNumber(numStack.pop());
	}
}

class MuaNumber extends Value implements Comparable<MuaNumber> {
	private static final long serialVersionUID = 0x3180101729002000L;
	private double value;
	static final MuaNumber ONE  = new MuaNumber(1.0);
	static final MuaNumber ZERO = new MuaNumber(0.0);
	MuaNumber(double value) { this.value = value; }
	MuaNumber(String str) { this.value = Double.parseDouble(str); }
	static boolean isNumber(String str) {	//is a number literal
		//System.err.println("isNumber(" + str + ")");
		for(int i = 0; i < str.length(); ++i) {
			char c = str.charAt(i);
			if(!Character.isDigit(c) && c != '-' && c != '.')
				return false;
		}
		return true;
	}
	static boolean isNumber(Value value) {	//is a number subclass
		if(value == null)
			return false;
		else if(value instanceof MuaNumber)
			return true;
		else if(value instanceof MuaWord)	//or a word can be convert to number
			return isNumber(((MuaWord)value).getValue());
		else
			return false;
	}
	static MuaNumber toNumber(Value value) {
		if(value instanceof MuaNumber)
			return (MuaNumber)value;
		else if(value instanceof MuaWord)
			return new MuaNumber(((MuaWord)value).getValue());
		else if(value instanceof MuaBoolean)
			return ((MuaBoolean)value).getValue() ? ONE : ZERO;
		//TODO
		//System.err.println(value.toString() + " is not a number!");
		return ZERO;
	}
	double getValue() { return this.value; }
	public int compareTo(MuaNumber rhs) {
		double t = value - rhs.value;
		if(t < -1e-8) return -1;
		else if(t > 1e-8) return 1;
		else return 0;
	}
	public String toString() { return "" + this.value; }
}

class MuaWord extends Value implements Comparable<MuaWord> {
	private static final long serialVersionUID = 0x3180101729003000L;
	private String value;
	MuaWord(String value) { this.value = value; }
	static boolean isWord(String str) {		//is a word literal
		return str.charAt(0) == '\"';
	}
	static boolean isWord(Value value) {	//is word subclass
		if(value == null)
			return false;
		if(value instanceof MuaList)
			return false;
		if(value instanceof MuaWord)
			return true;
		return false;
	}
	static MuaWord toWord(Value value) {
		return new MuaWord(value.toString());
	}
	static MuaWord connect(MuaWord left, MuaWord right) {
		return new MuaWord(left.getValue() + right.getValue());
	}
	String getValue() { return this.value; }
	public int compareTo(MuaWord rhs) {
		return value.compareTo(rhs.value);
	}
	public String toString() { return this.value; }
}

class MuaBoolean extends Value {
	private static final long serialVersionUID = 0x3180101729004000L;
	private Boolean value;
	MuaBoolean(Boolean value) { this.value = value; }
	MuaBoolean(String str) { this.value = str.equals("true") ? true : false; }
	static boolean isBoolean(String str) {		//is a bool literal
		return str.equals("true") || str.equals("false");
	}
	static boolean isBoolean(Value value) {		//is bool subclass
		if(value == null)
			return false;
		else if(value instanceof MuaBoolean)
			return true;
		else if(value instanceof MuaWord)		//or a word can be convert to boolean
			return isBoolean(((MuaWord)value).getValue());
		else
			return false;
	}
	static MuaBoolean toBoolean(Value value) {
		if(value instanceof MuaBoolean)
			return (MuaBoolean)value;
		else if(value instanceof MuaWord) {
			MuaWord val = (MuaWord)value;
			if(isBoolean(val.getValue()))
				return new MuaBoolean(val.getValue().equals("true"));
		}
		//TODO
		return new MuaBoolean(true);
	}
	boolean getValue() { return this.value; }
	public String toString() { return "" + this.value; }
}

class MuaName implements Serializable {
	private static final long serialVersionUID = 0x3180101729030000L;
	private String name;
	private Value value;
	private boolean isFunction;
	MuaName(String name, Value value) {
		this.name = name;
		this.value = value;
		this.isFunction = false;
		if(MuaList.isFunction(this.value))
			this.isFunction = true;
	}
	MuaName(String name) {
		this(name, null);
	}

	String getName() { return this.name; }
	void setName(String name) { this.name = name; }
	Value getValue() { return this.value; }
	void setValue(Value value) { this.value = value;}
	boolean isFunction() { return this.isFunction; }
	boolean isEmpty() { return this.value == null; }

	static boolean isName(String str) {		//is a name literal
		if(!Character.isAlphabetic(str.charAt(0)))
			return false;
		for(int i = 1; i < str.length(); ++i) {
			char c = str.charAt(i);
			if(!Character.isDigit(c) && !Character.isAlphabetic(c) && c != '_')
				return false;
		}
		return true;
	}

	static boolean isName(Value value) {
		if(value instanceof MuaWord) {
			String str = ((MuaWord)value).getValue();
			if(isName(str))
				return ArguTable.existName(str);
		}
		return false;
	}

	static String toName(Value value) {
		//System.err.println("toName(" + value.toString() + ")");
		if(value instanceof MuaWord) {
			String name = ((MuaWord)value).getValue();
			if(isName(name))
				return name;
		}
		//TODO
		//System.err.println(value.toString() + " is not a name!");
		return "";
	}
}



enum Operation {

	MAKE("make", 2) {
		Value exec(Value[] args) {
			//System.err.println("make " + args[0].toString() + " " + args[1].toString());
			ArguTable.modify(MuaName.toName(args[0]), args[1]);
			return args[1];
		}
	},
	
	THING("thing", 1) {
		Value exec(Value[] args) {
			String name = MuaName.toName(args[0]);
			//System.err.println("thing " + name);
			return ArguTable.getName(name).getValue();
		}
	},


	PRINT("print", 1) {
		Value exec(Value[] args) {
			//System.err.println("Operation$print:exec");
			out.println(args[0].toString());
			return args[0];
		}
	},

	READ("read", 0) {
		Value exec(Value[] args) {
			//System.err.println("Operation$read:exec");
			return new MuaWord(in.next());	//static Scanner in
		}
	},

	ADD("add", 2) {		//$5
		Value exec(Value[] args) {
			MuaNumber lhs = MuaNumber.toNumber(args[0]);
			MuaNumber rhs = MuaNumber.toNumber(args[1]);
			double x = lhs.getValue(), y = rhs.getValue();
			//System.err.println("add " + x + " " + y);
			return new MuaNumber(x + y);
		}
	},

	SUB("sub", 2) {
		Value exec(Value[] args) {
			MuaNumber lhs = MuaNumber.toNumber(args[0]);
			MuaNumber rhs = MuaNumber.toNumber(args[1]);
			double x = lhs.getValue(), y = rhs.getValue();
			//System.err.println("sub " + x + " " + y);
			return new MuaNumber(x - y);
		}
	},

	MUL("mul", 2) {
		Value exec(Value[] args) {
			MuaNumber lhs = MuaNumber.toNumber(args[0]);
			MuaNumber rhs = MuaNumber.toNumber(args[1]);
			double x = lhs.getValue(), y = rhs.getValue();
			//System.err.println("mul " + x + " " + y);
			return new MuaNumber(x * y);
		}
	},

	DIV("div", 2) {
		Value exec(Value[] args) {
			MuaNumber lhs = MuaNumber.toNumber(args[0]);
			MuaNumber rhs = MuaNumber.toNumber(args[1]);
			double x = lhs.getValue(), y = rhs.getValue();
			//System.err.println("div " + x + " " + y);
			return new MuaNumber(x / y);
		}
	},

	MOD("mod", 2) {
		Value exec(Value[] args) {
			MuaNumber lhs = MuaNumber.toNumber(args[0]);
			MuaNumber rhs = MuaNumber.toNumber(args[1]);
			double x = lhs.getValue(), y = rhs.getValue();
			//System.err.println("mod " + x + " " + y);
			return new MuaNumber(x - Math.floor(x / y) * y);
		}
	},

	//-----------
	EQ("eq", 2) {		//$10
		Value exec(Value[] args) {
			if(MuaNumber.isNumber(args[0]) && MuaNumber.isNumber(args[1])) {
				MuaNumber lhs = MuaNumber.toNumber(args[0]);
				MuaNumber rhs = MuaNumber.toNumber(args[1]);
				return new MuaBoolean(lhs.compareTo(rhs) == 0);
			}else if(MuaWord.isWord(args[0]) && MuaWord.isWord(args[1])) {
				MuaWord lhs = MuaWord.toWord(args[0]);
				MuaWord rhs = MuaWord.toWord(args[1]);
				return new MuaBoolean(lhs.compareTo(rhs) == 0);
			}
			return new MuaBoolean(false);
		}
	},

	GT("gt", 2) {
		Value exec(Value[] args) {
			if(MuaNumber.isNumber(args[0]) && MuaNumber.isNumber(args[1])) {
				MuaNumber lhs = MuaNumber.toNumber(args[0]);
				MuaNumber rhs = MuaNumber.toNumber(args[1]);
				return new MuaBoolean(lhs.compareTo(rhs) > 0);
			}else if(MuaWord.isWord(args[0]) && MuaWord.isWord(args[1])) {
				MuaWord lhs = MuaWord.toWord(args[0]);
				MuaWord rhs = MuaWord.toWord(args[1]);
				return new MuaBoolean(lhs.compareTo(rhs) > 0);
			}
			return new MuaBoolean(false);
		}
	},
	
	LT("lt", 2) {
		Value exec(Value[] args) {
			if(MuaNumber.isNumber(args[0]) && MuaNumber.isNumber(args[1])) {
				MuaNumber lhs = MuaNumber.toNumber(args[0]);
				MuaNumber rhs = MuaNumber.toNumber(args[1]);
				return new MuaBoolean(lhs.compareTo(rhs) < 0);
			}else if(MuaWord.isWord(args[0]) && MuaWord.isWord(args[1])) {
				MuaWord lhs = MuaWord.toWord(args[0]);
				MuaWord rhs = MuaWord.toWord(args[1]);
				return new MuaBoolean(lhs.compareTo(rhs) < 0);
			}
			return new MuaBoolean(false);
		}
	},

	AND("and", 2) {
		Value exec(Value[] args) {
			MuaBoolean lhs = MuaBoolean.toBoolean(args[0]);
			MuaBoolean rhs = MuaBoolean.toBoolean(args[1]);
			return new MuaBoolean(lhs.getValue() & rhs.getValue());
		}
	},

	OR("or", 2) {
		Value exec(Value[] args) {
			MuaBoolean lhs = MuaBoolean.toBoolean(args[0]);
			MuaBoolean rhs = MuaBoolean.toBoolean(args[1]);
			return new MuaBoolean(lhs.getValue() | rhs.getValue());
		}
	},

	NOT("not", 1) {			//$15
		Value exec(Value[] args) {
			MuaBoolean lhs = MuaBoolean.toBoolean(args[0]);
			return new MuaBoolean(!lhs.getValue());
		}
	},

	ERASE("erase", 1) {
		Value exec(Value[] args) {
			String name = MuaName.toName(args[0]);
			if(!ArguTable.existName(name))
				return null;//TODO
			else
				return ArguTable.erase(name);
		}
	},

	ISEMPTY("isempty", 1) {
		Value exec(Value[] args) {
			if(args[0] instanceof MuaList)
				return new MuaBoolean(((MuaList)args[0]).isEmpty());
			else {
				String name = MuaWord.toWord(args[0]).getValue();
				if(ArguTable.existName(name))
					return new MuaBoolean(ArguTable.getName(name).isEmpty());
				//TODO
			}
			return new MuaBoolean(false);
		}
	},

	ISWORD("isword", 1) {
		Value exec(Value[] args) {
			return new MuaBoolean(MuaWord.isWord(args[0]));
		}
	},

	ISNUMBER("isnumber", 1) {
		Value exec(Value[] args) {
			return new MuaBoolean(MuaNumber.isNumber(args[0]));
		}
	},

	ISBOOL("isbool", 1) {	//$20
		Value exec(Value[] args) {
			return new MuaBoolean(MuaBoolean.isBoolean(args[0]));
		}
	},

	ISLIST("islist", 1) {
		Value exec(Value[] args) {
			return new MuaBoolean(MuaList.isList(args[0]));
		}
	},

	ISNAME("isname", 1) {
		Value exec(Value[] args) {
			return new MuaBoolean(MuaName.isName(args[0]));
		}
	},

	RUN("run", 1) {
		Value exec(Value[] args) {
			return run(MuaList.toList(args[0]));
		}
	},

	RETURN("return", 1) {
		Value exec(Value[] args) {
			isReturn = true;
			return args[0];
		}
	},

	EXPORT("export", 1) {		//$25
		Value exec(Value[] args) {
			return ArguTable.export(MuaName.toName(args[0]));
		}
	},

	IF("if", 3) {
		Value exec(Value[] args) {
			if(MuaBoolean.toBoolean(args[0]).getValue())
				return run(MuaList.toList(args[1]));
			else
				return run(MuaList.toList(args[2]));
		}
	},

	READLIST("readlist", 0) {
		Value exec(Value[] args) {
			return new MuaList(in.nextLine().split(" "));
		}
	},

	WORD("word", 2) {
		Value exec(Value[] args) {
			return MuaWord.connect(MuaWord.toWord(args[0]), MuaWord.toWord(args[1]));
		}
	},

	SENTENCE("sentence", 2) {
		Value exec(Value[] args) {
			Value[] lhs = Value.toArray(args[0]);
			Value[] rhs = Value.toArray(args[1]);
			ArrayList<Value> arr = new ArrayList<Value>(Arrays.asList(lhs));
			arr.addAll(Arrays.asList(rhs));
			return new MuaList(arr.toArray(new Value[0]));
		}
	},

	LIST("list", 2) {		//$30
		Value exec(Value[] args) {
			return new MuaList(args);
		}
	},
	
	JOIN("join", 2) {
		Value exec(Value[] args) {
			Value[] values = MuaList.toValueArray(MuaList.toList(args[0]));
			ArrayList<Value> arr;
			if(values != null)
				arr = new ArrayList<Value>(Arrays.asList(values));
			else
				arr = new ArrayList<Value>();
			arr.add(args[1]);
			return new MuaList(arr.toArray(new Value[0]));
		}
	},

	FIRST("first", 1) {
		Value exec(Value[] args) {
			//System.err.println("first: " + args[0].toString());
			if(MuaList.isList(args[0])) {
				Value[] values = MuaList.toValueArray(MuaList.toList(args[0]));
				return values[0];
			}else {
				String str = MuaWord.toWord(args[0]).getValue();
				return new MuaWord(str.substring(0, 1));
			}
		}
	},

	LAST("last", 1) {
		Value exec(Value[] args) {
			if(MuaList.isList(args[0])) {
				Value[] values = MuaList.toValueArray(MuaList.toList(args[0]));
				return values[values.length - 1];
			}else {
				String str = MuaWord.toWord(args[0]).getValue();
				return new MuaWord(str.substring(str.length() - 1, str.length()));
			}
		}
	},

	BUTFIRST("butfirst", 1) {
		Value exec(Value[] args) {
			if(MuaList.isList(args[0])) {
				Value[] values = MuaList.toValueArray(MuaList.toList(args[0]));
				return new MuaList(Arrays.copyOfRange(values, 1, values.length));
			}else {
				String str = MuaWord.toWord(args[0]).getValue();
				return new MuaWord(str.substring(1, str.length()));
			}
		}
	},

	BUTLAST("butlast", 1) {		//$35
		Value exec(Value[] args) {
			if(MuaList.isList(args[0])) {
				Value[] values = MuaList.toValueArray(MuaList.toList(args[0]));
				return new MuaList(Arrays.copyOfRange(values, 0, values.length - 1));
			}else {
				String str = MuaWord.toWord(args[0]).getValue();
				return new MuaWord(str.substring(0, str.length() - 1));
			}
		}
	},

	RANDOM("random", 1) {
		Value exec(Value[] args) {
			int x = (int)MuaNumber.toNumber(args[0]).getValue();
			double rnd = Math.random();
			return new MuaNumber((int)(x * rnd));
		}
	},

	INT("int", 1) {
		Value exec(Value[] args) {
			return new MuaNumber((int)MuaNumber.toNumber(args[0]).getValue());
		}
	},

	SQRT("sqrt", 1) {
		Value exec(Value[] args) {
			return new MuaNumber(Math.sqrt(MuaNumber.toNumber(args[0]).getValue()));
		}
	},

	SAVE("save", 1) {
		Value exec(Value[] args) {
			ArguTable.save(MuaWord.toWord(args[0]).getValue());
			return args[0];
		}
	},

	LOAD("load", 1) {		//$40
		Value exec(Value[] args) {
			ArguTable.load(MuaWord.toWord(args[0]).getValue());
			return new MuaBoolean(true);
		}
	},

	ERALL("erall", 0) {
		Value exec(Value[] args) {
			ArguTable.clear();
			return new MuaBoolean(true);
		}
	},

	POALL("poall", 0) {
		Value exec(Value[] args) {
			return new MuaList(ArguTable.getAll());
		}
	};

	private final String name;
	private final int argNum;

	private Operation(String name, int num) {
		this.name = name;
		this.argNum = num;
	}

	abstract Value exec(Value[] args);

	private Value exec(Scanner in) {
		return exec(getArgs(in));
	}
	private Value[] getArgs(Scanner in) {
		Value[] args = new Value[argNum];
		for(int i = 0; i < argNum; ++i)
			args[i] = Exec(in);
		return args;
	}
	static Value Exec(Scanner in) {					//exec the first command
		//System.err.println("Operation.Exec(Scanner)");
		String str = in.next();
		if(str.charAt(0) == ':')		//special operation
			return ArguTable.getName(str.substring(1, str.length())).getValue();
		else if(Value.isValue(str))		//a literal
			return Value.toValue(str, in);
		if(ArguTable.existName(str)) {
			MuaName name = ArguTable.getName(str);
			if(name.isFunction())
				return Exec(name, in);
		}
		for(Operation op : Operation.values())
			if(str.equals(op.name))
				return op.exec(in);
		//System.err.println("unrecognize");
		return null;
	}

	private Value exec(StringStream stm) {
		//System.err.println("Operation%.exec(StringStream): " + stm.toString() + "\tnow: " + stm.now);
		return exec(getArgs(stm));
	}
	private Value[] getArgs(StringStream stm) {
		//System.err.println("Operation%.getArgs(StringStream): " + stm.toString() + "\tnow: " + stm.now);
		Value[] args = new Value[argNum];
		for(int i = 0; i < argNum; ++i)
			args[i] = Exec(stm);
		return args;
	}
	static Value Exec(StringStream stm) {			//exec the first command
		//System.err.println("Operation.Exec(StringStream): " + stm.toString() + "\tnow: " + stm.now);
		String str = stm.next();
		//System.err.println("stm.next(): " + str);
		if(str.charAt(0) == ':')
			return ArguTable.getName(str.substring(1, str.length())).getValue();
		else if(Value.isValue(str))
			return Value.toValue(str, stm);
		if(ArguTable.existName(str)) {
			MuaName name = ArguTable.getName(str);
			if(name.isFunction())
				return Exec(name, stm);
		}
		for(Operation op : Operation.values())
			if(str.equals(op.name))
				return op.exec(stm);
		//System.err.println("unrecognize");
		return null;
	}
	static Value run(StringStream stm) {
		//System.err.println("Operation.run(StringStream): " + stm.toString() + "\tnow: " + stm.now);
		Value ret = null;
		while(stm.hasNext())
			ret = Exec(stm);
		return ret;
	}
	static Value runFunction(StringStream stm) {
		//System.err.println("Operation.runFunction(StringStream): " + stm.toString() + "\tnow: " + stm.now);
		Value ret = null;
		while(stm.hasNext() && !isReturn)
			ret = Exec(stm);
		isReturn = false;
		return ret;
	}
	static Value run(MuaList list) {
		if(list.isEmpty())
			return new MuaList();
		return run(list.getValue().reset());
	}

	static Value Exec(MuaName name, Scanner in) {
		//System.err.println("Operation.Exec(MuaName, Scanner);");
		StringStream stm = ((MuaList)name.getValue()).getValue().reset();
		StringStream args = MuaList.getFirstList(stm).getValue();
		StringStream ops  = MuaList.getFirstList(stm).getValue();
		//TODO better
		HashMap<String, MuaName> arguMap = new HashMap<String, MuaName>();
		while(args.hasNext()) {
			String str = args.next();
			ArguTable.modify(arguMap, str, Exec(in));
		}
		ArguTable.push(arguMap);
		Value ret = runFunction(ops);
		ArguTable.pop();
		return ret;
	}

	static Value Exec(MuaName name, StringStream in) {
		//System.err.println("Operation.Exec(MuaName: " + name.getName() + ", StringStream): " + in.toString() + "\tnow: " + in.now);
		StringStream stm = ((MuaList)name.getValue()).getValue().reset();
		StringStream args = MuaList.getFirstList(stm).getValue();
		StringStream ops  = MuaList.getFirstList(stm).getValue();
		//TODO
		HashMap<String, MuaName> arguMap = new HashMap<String, MuaName>();
		while(args.hasNext()) {
			String str = args.next();
			ArguTable.modify(arguMap, str, Exec(in));
		}
		ArguTable.push(arguMap);
		Value ret = runFunction(ops);
		ArguTable.pop();
		return ret;
	}

	static boolean isReturn = false;
	static Scanner in = null;
	static PrintStream out = null;
	static void init(InputStream source, PrintStream dest) {
		in = new Scanner(source);
		out = dest;
	}
	static Value run() {
		//System.err.println("Operation.run()");
		Value ret = null;
		while(in.hasNext())
			ret = Exec(in);
		return ret;
	}
}

class ArguTable {
	static ArrayList<HashMap<String, MuaName>> mapList;
	static HashMap<String, MuaName> arguMap;
	static HashMap<String, MuaName> arguMapBase;
	static int top;

	static {
		mapList = new ArrayList<HashMap<String, MuaName>>();
		arguMap = arguMapBase = new HashMap<String, MuaName>();
		mapList.add(arguMapBase);
		top = 1;
		modify("pi", new MuaNumber(3.14159));
	}
	
	static void push(HashMap<String, MuaName> arg) {
		mapList.add(arg);
		arguMap = mapList.get(top++);
	}
	static void push() {
		mapList.add(new HashMap<String, MuaName>());
		arguMap = mapList.get(top++);
	}
	static void pop() {
		mapList.remove(--top);
		arguMap = mapList.get(top - 1);
	}

	static boolean existName(String name) {				//read both
		return arguMap.containsKey(name) || arguMapBase.containsKey(name);
	}

	static MuaName getName(String name) {				//read both
		if(arguMap.containsKey(name))
			return arguMap.get(name);
		else if(arguMapBase.containsKey(name))
			return arguMapBase.get(name);
		else
			return null;	//TODO
	}

	static void modify(String name, Value value) {		//make local
		//System.err.println("ArguTable.modify(" + name + "," + value.toString() + ")");
		if(arguMap.containsKey(name))
			arguMap.get(name).setValue(value);
		else
			arguMap.put(name, new MuaName(name, value));
	}

	static void modify(HashMap<String, MuaName> arguMap, String name, Value value) {		//make local
		//System.err.println("ArguTable.modify(arguMap, " + name + "," + value.toString() + ")");
		if(arguMap.containsKey(name))
			arguMap.get(name).setValue(value);
		else
			arguMap.put(name, new MuaName(name, value));
	}

	static Value export(String name) {
		Value value = arguMap.get(name).getValue();
		if(arguMapBase.containsKey(name))
			arguMapBase.get(name).setValue(value);
		else
			arguMapBase.put(name, new MuaName(name, value));
		return value;
	}

	static Value erase(String name) {		//erase undefined
		MuaName Name = getName(name);
		Value value = Name.getValue();
		arguMap.remove(Name.getName());
		return value;
	}

	static void save(String fileName) {
		try {
			ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(fileName));
			out.writeObject(mapList);
			out.writeObject(arguMap);
			out.writeObject(arguMapBase);
			out.writeObject(top);
			out.close();
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	@SuppressWarnings("unchecked")
	static void load(String fileName) {
		try {
			ObjectInputStream in = new ObjectInputStream(new FileInputStream(fileName));
			mapList = (ArrayList<HashMap<String, MuaName>>)in.readObject();
			arguMap = (HashMap<String, MuaName>)in.readObject();
			arguMapBase = (HashMap<String, MuaName>)in.readObject();
			top = (int)in.readObject();
			in.close();
		}catch(Exception e) {
			System.out.println("bbbb");
			//e.printStackTrace();
		}
	}

	static void clear() {
		arguMap.clear();
	}

	static String[] getAll() {
		return null;
	}
}

public class Main {
	public static void main(String args[]) {
		Operation.init(System.in, System.out);
		Operation.run();
	}
}

