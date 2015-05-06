package code.docflow.utils;

//
// Author: Alexey Zorkaltsev (alexey@zorkaltsev.com)
//

/**
 * Supports expresions with fields and actions definition.
 */
public abstract class GroupCalculator {

    public static class ErrorException extends Exception {
        public final Error error;
        public final int pos;
        public String value;

        public ErrorException(Error error, int pos, String value) {
            this.error = error;
            this.pos = pos;
            this.value = value;
        }
    }
    
    String nameOrExpr;
    int pos;
    int wordStartPosition;
    int startPos;

    protected enum Error {
        unexpectedSymbol,
        notMatchingParenthesises,
        unexpectedEndOfExpression,
        unknownItem,
        unknownGroup,
    }

    enum Token {
        inverse,
        plus,
        minus,
        and,
        item,
        group,
        leftParenthesis,
        rightParenthesis,
        end
    }

    String tokenValue; // for item and group tokens.

    public BitArray parse(String nameOrExpr) {
        this.nameOrExpr = nameOrExpr;
        this.pos = 0;
        try {
            return parseLevel(true, false);
        } catch (ErrorException err) {
            reportError(err.error, err.pos, err.value);
            return null;
        }
    }
    
    protected BitArray parseLevel(boolean topLevel, boolean toParenthesis) throws ErrorException {
        Token token;
        BitArray res = null;
    processing:
        while ((token = nextToken()) != null) {
            switch (token) {
                case item:
                    int itemIndex = getItemIndex(tokenValue);
                    if (itemIndex < 0)
                        throw new ErrorException(Error.unknownItem, startPos, tokenValue);
                    if (res == null)
                        res = newBitArray();
                    res.set(itemIndex, true);
                    if (!topLevel && !toParenthesis)
                        return res;
                    break;
                case group:
                    BitArray group = getGroup(tokenValue);
                    if (group == null)
                        throw new ErrorException(Error.unknownGroup, startPos, tokenValue);
                    if (res == null)
                        res = group.copy();
                    else
                        res.add(group);
                    if (!topLevel && !toParenthesis)
                        return res;
                    break;
                case plus: // spaces or commas considered as 'add'
                    break;
                case leftParenthesis:
                    group = parseLevel(false, true);
                    if (res == null)
                        res = group.copy();
                    else
                        res.add(group);
                case rightParenthesis:
                    return res;
                case inverse:
                    BitArray bitArray = parseLevel(false, false);
                    bitArray.inverse();
                    if (res == null)
                        res = bitArray;
                    else
                        res.add(bitArray);
                    break;
                case minus:
                    if (res == null)
                        throw new ErrorException(Error.unexpectedSymbol, pos, "" + nameOrExpr.charAt(pos));
                    res.subtract(parseLevel(false, false));
                    break;
                case and:
                    if (res == null)
                        throw new ErrorException(Error.unexpectedSymbol, pos, "" + nameOrExpr.charAt(pos));
                    res.intersect(parseLevel(false, false));
                    break;
                case end:
                    if (toParenthesis)
                        throw new ErrorException(Error.notMatchingParenthesises, 0, "");
                    if (res == null)
                        throw new ErrorException(Error.unexpectedEndOfExpression, 0, "");
                    break processing;
            }
        }
        return res;
    }



    private Token nextToken() throws ErrorException {
        int s = 0;
        for (; pos < nameOrExpr.length(); pos++) {
            char c = nameOrExpr.charAt(pos);
            switch (c) {
                case '!':
                    pos++;
                    return Token.inverse;
                case ',':
                    pos++;
                    return Token.plus;
                case '&':
                    pos++;
                    return Token.and;
                case '-':
                    pos++;
                    return Token.minus;
                case '(':
                    pos++;
                    return Token.leftParenthesis;
                case ')':
                    pos++;
                    return Token.rightParenthesis;
                case '_':
                    s = 2; // group beginning
                    pos++;
                    // fallthru
                default:
                    if (Character.isWhitespace(c) && s == 0)
                        continue;
                    for (; pos < nameOrExpr.length(); pos++) {
                        c = nameOrExpr.charAt(pos);
                        switch (s) {
                            case 0:
                                if (Character.isLetter(c)) {
                                    s = 1;
                                    startPos = pos;
                                }
                                break;
                            case 1:
                                if (!Character.isLetterOrDigit(c)) {
                                    tokenValue = nameOrExpr.substring(startPos, pos);
                                    return Token.item;
                                }
                                break;
                            case 2:
                                if (Character.isLetter(c)) {
                                    s = 3;
                                    startPos = pos;
                                } else
                                    throw new ErrorException(Error.unexpectedSymbol, pos, "" + nameOrExpr.charAt(pos));
                                break;
                            case 3:
                                if (!Character.isLetterOrDigit(c)) {
                                    tokenValue = nameOrExpr.substring(startPos, pos);
                                    return Token.group;
                                }
                                break;

                        }
                        if (s == 0)
                            throw new ErrorException(Error.unexpectedSymbol, pos, "" + nameOrExpr.charAt(pos));
                    };
            }
        }
        switch (s) {
            case 1:
                tokenValue = nameOrExpr.substring(startPos, pos - 1);
                return Token.item;
            case 2:
                throw new ErrorException(Error.unexpectedEndOfExpression, 0, "");
            case 3:
                tokenValue = nameOrExpr.substring(startPos, pos - 1);
                return Token.group;
        }
        return Token.end;
    }

    public abstract BitArray newBitArray();

    public abstract BitArray getGroup(String name);

    public abstract int getItemIndex(String name);

    public abstract void reportError(Error error, int position, String value);
}
