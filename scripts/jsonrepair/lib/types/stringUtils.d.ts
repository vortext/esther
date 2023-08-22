export declare const codeBackslash = 92;
export declare const codeSlash = 47;
export declare const codeAsterisk = 42;
export declare const codeOpeningBrace = 123;
export declare const codeClosingBrace = 125;
export declare const codeOpeningBracket = 91;
export declare const codeClosingBracket = 93;
export declare const codeOpenParenthesis = 40;
export declare const codeCloseParenthesis = 41;
export declare const codeSpace = 32;
export declare const codeNewline = 10;
export declare const codeTab = 9;
export declare const codeReturn = 13;
export declare const codeBackspace = 8;
export declare const codeFormFeed = 12;
export declare const codeDoubleQuote = 34;
export declare const codePlus = 43;
export declare const codeMinus = 45;
export declare const codeQuote = 39;
export declare const codeZero = 48;
export declare const codeOne = 49;
export declare const codeNine = 57;
export declare const codeComma = 44;
export declare const codeDot = 46;
export declare const codeColon = 58;
export declare const codeSemicolon = 59;
export declare const codeUppercaseA = 65;
export declare const codeLowercaseA = 97;
export declare const codeUppercaseE = 69;
export declare const codeLowercaseE = 101;
export declare const codeUppercaseF = 70;
export declare const codeLowercaseF = 102;
export declare function isHex(code: number): boolean;
export declare function isDigit(code: number): boolean;
export declare function isNonZeroDigit(code: number): boolean;
export declare function isValidStringCharacter(code: number): boolean;
export declare function isDelimiter(char: string): boolean;
export declare function isStartOfValue(char: string): boolean;
export declare function isControlCharacter(code: number): boolean;
/**
 * Check if the given character is a whitespace character like space, tab, or
 * newline
 */
export declare function isWhitespace(code: number): boolean;
/**
 * Check if the given character is a special whitespace character, some
 * unicode variant
 */
export declare function isSpecialWhitespace(code: number): boolean;
/**
 * Test whether the given character is a quote or double quote character.
 * Also tests for special variants of quotes.
 */
export declare function isQuote(code: number): boolean;
/**
 * Test whether the given character is a double quote character.
 * Also tests for special variants of double quotes.
 */
export declare function isDoubleQuoteLike(code: number): boolean;
/**
 * Test whether the given character is a double quote character.
 * Does NOT test for special variants of double quotes.
 */
export declare function isDoubleQuote(code: number): boolean;
/**
 * Test whether the given character is a single quote character.
 * Also tests for special variants of single quotes.
 */
export declare function isSingleQuoteLike(code: number): boolean;
/**
 * Strip last occurrence of textToStrip from text
 */
export declare function stripLastOccurrence(text: string, textToStrip: string, stripRemainingText?: boolean): string;
export declare function insertBeforeLastWhitespace(text: string, textToInsert: string): string;
export declare function removeAtIndex(text: string, start: number, count: number): string;
/**
 * Test whether a string ends with a newline or comma character and optional whitespace
 */
export declare function endsWithCommaOrNewline(text: string): boolean;
//# sourceMappingURL=stringUtils.d.ts.map