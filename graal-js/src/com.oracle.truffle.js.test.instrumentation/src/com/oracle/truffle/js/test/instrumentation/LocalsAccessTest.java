/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.test.instrumentation;

import org.junit.Test;

import com.oracle.truffle.js.nodes.instrumentation.JSTags.BinaryExpressionTag;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.EvalCallTag;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.FunctionCallExpressionTag;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.LiteralExpressionTag;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.ReadPropertyExpressionTag;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.UnaryExpressionTag;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.WritePropertyExpressionTag;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.ReadVariableExpressionTag;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.WriteVariableExpressionTag;
import com.oracle.truffle.js.runtime.objects.Undefined;

public class LocalsAccessTest extends FineGrainedAccessTest {

    @Test
    public void write() {
        evalAllTags("(function() { var a = 42; })();");

        enter(WriteVariableExpressionTag.class, (e, write) -> {
            enter(FunctionCallExpressionTag.class, (e1, call) -> {
                // fetch the target for the call (which is undefined)
                enter(LiteralExpressionTag.class).exit(assertReturnValue(Undefined.instance));
                call.input(assertUndefinedInput);
                // fetch the function, i.e., read the literal
                enter(LiteralExpressionTag.class).exit((e2) -> {
                    assertAttribute(e2, TYPE, LiteralExpressionTag.Type.FunctionLiteral.name());
                });
                call.input(assertJSFunctionInput);
                enter(UnaryExpressionTag.class, (e2, unary) -> {
                    enter(WriteVariableExpressionTag.class, (e3, var) -> {
                        enter(LiteralExpressionTag.class).exit();
                        var.input(42);
                    }).exit();
                    unary.input(42);
                }).exit();
            }).exit();
            write.input(Undefined.instance);
        }).exit();
    }

    @Test
    public void writeScope() {
        evalAllTags("(function() { var level; (function() { (function() { level = 42; })(); })();})();");

        enter(WriteVariableExpressionTag.class, (e, write) -> {
            // first call
            enter(FunctionCallExpressionTag.class, (e1, call) -> {
                enter(LiteralExpressionTag.class).exit(assertReturnValue(Undefined.instance));
                call.input(assertUndefinedInput);
                enter(LiteralExpressionTag.class).exit((e2) -> {
                    assertAttribute(e2, TYPE, LiteralExpressionTag.Type.FunctionLiteral.name());
                });
                call.input(assertJSFunctionInput);
                // second call
                enter(UnaryExpressionTag.class, (e6, unary) -> {
                    enter(FunctionCallExpressionTag.class, (e2, call2) -> {
                        enter(LiteralExpressionTag.class).exit(assertReturnValue(Undefined.instance));
                        call2.input(assertUndefinedInput);
                        enter(LiteralExpressionTag.class).exit((e3) -> {
                            assertAttribute(e3, TYPE, LiteralExpressionTag.Type.FunctionLiteral.name());
                        });
                        call2.input(assertJSFunctionInput);
                        // third call
                        enter(UnaryExpressionTag.class, (e7, unary2) -> {
                            enter(FunctionCallExpressionTag.class, (e3, call3) -> {
                                enter(LiteralExpressionTag.class).exit(assertReturnValue(Undefined.instance));
                                call3.input(assertUndefinedInput);
                                enter(LiteralExpressionTag.class).exit((e4) -> {
                                    assertAttribute(e4, TYPE, LiteralExpressionTag.Type.FunctionLiteral.name());
                                });
                                call3.input(assertJSFunctionInput);
                                // TODO missing input event for arguments to FunctionCallTag
                                enter(UnaryExpressionTag.class, (e8, unary3) -> {
                                    enter(WriteVariableExpressionTag.class, (e4, var) -> {
                                        enter(LiteralExpressionTag.class).exit();
                                        var.input(42);
                                    }).exit();
                                    unary3.input();
                                }).exit();
                            }).exit();
                            unary2.input();
                        }).exit();
                    }).exit();
                    unary.input();
                }).exit();
            }).exit();
            write.input(Undefined.instance);
        }).exit();
    }

    @Test
    public void read() {
        evalAllTags("(function() { var a = 42; return a; })();");

        enter(WriteVariableExpressionTag.class, (e, write) -> {
            enter(FunctionCallExpressionTag.class, (e1, call) -> {
                // fetch the target for the call (which is undefined)
                enter(LiteralExpressionTag.class).exit(assertReturnValue(Undefined.instance));
                call.input(assertUndefinedInput);
                // get the function from the literal
                enter(LiteralExpressionTag.class).exit((e2) -> {
                    assertAttribute(e2, TYPE, LiteralExpressionTag.Type.FunctionLiteral.name());
                });
                call.input(assertJSFunctionInput);
                // write 42
                enter(WriteVariableExpressionTag.class, (e2, var) -> {
                    enter(LiteralExpressionTag.class).exit();
                    var.input(42);
                }).exit();
                // return statement
                enter(ReadVariableExpressionTag.class).exit();
            }).exit();
            write.input(42);
        }).exit();
    }

    @Test
    public void scopedRead() {
        evalAllTags("(function() { function foo(a){ return a; }; foo(42); })();");

        for (Event event : events) {
            System.out.println(event.debug());
        }
        enter(WriteVariableExpressionTag.class, (e, write) -> {
            enter(FunctionCallExpressionTag.class, (e1, call) -> {
                // fetch the target for the call (which is undefined)
                enter(LiteralExpressionTag.class).exit(assertReturnValue(Undefined.instance));
                call.input(assertUndefinedInput);
                // get the function from the literal
                enter(LiteralExpressionTag.class).exit((e2) -> {
                    assertAttribute(e2, TYPE, LiteralExpressionTag.Type.FunctionLiteral.name());
                });
                call.input(assertJSFunctionInput);
                // write foo
                enter(WriteVariableExpressionTag.class, (e2, write2) -> {
                    enter(LiteralExpressionTag.class).exit((e3) -> {
                        assertAttribute(e3, TYPE, LiteralExpressionTag.Type.FunctionLiteral.name());
                    });
                    write2.input(assertJSFunctionInput);
                }).exit();
                enter(FunctionCallExpressionTag.class, (e2, call2) -> {
                    enter(LiteralExpressionTag.class).exit(assertReturnValue(Undefined.instance));
                    call.input(assertUndefinedInput);
                    enter(ReadVariableExpressionTag.class).exit();
                    call.input(assertJSFunctionInput);
                    enter(LiteralExpressionTag.class).exit();
                    call.input(42);
                    // return statement
                    enter(ReadVariableExpressionTag.class).exit();
                }).exit();
                // return statement
                enter(ReadVariableExpressionTag.class).exit();
            }).exit();
            write.input(42);
        }).exit();
    }

    @Test
    public void evalReadWrite() {
        evalAllTags("function foo(a) { \n" +
                        "  function bar() { \n" +
                        "    eval('var a = 30'); \n" +
                        "    return a++; \n" +
                        "  } \n" +
                        "  return bar;\n" +
                        "}\n" +
                        "foo(42)();");
        int cnt = 0;
        for (Event event : events) {
            System.out.println(cnt++ + ":" + event.debug());
        }

        enter(WritePropertyExpressionTag.class, (e, wp) -> {
            wp.input(assertGlobalObjectInput);
            enter(LiteralExpressionTag.class).exit((e2) -> {
                assertAttribute(e2, TYPE, LiteralExpressionTag.Type.FunctionLiteral.name());
            });
            wp.input(assertJSFunctionInput);
        }).exit();

        // foo(42)();
        enter(FunctionCallExpressionTag.class, (e1, call) -> {
            // fetch the target for the call (which is undefined)
            enter(LiteralExpressionTag.class).exit(assertReturnValue(Undefined.instance));
            call.input(assertUndefinedInput);

            enter(FunctionCallExpressionTag.class, (e2, call2) -> {
                enter(LiteralExpressionTag.class).exit(assertReturnValue(Undefined.instance));
                call.input(assertUndefinedInput);
                // get the function from the property foo
                enter(ReadPropertyExpressionTag.class, (e3, pr) -> {
                    assertAttribute(e3, KEY, "foo");
                    pr.input(assertGlobalObjectInput);
                }).exit();
                call.input(assertJSFunctionInput);
                enter(LiteralExpressionTag.class).exit();
                call.input(42);
                // in the foo function
                // write the 42 into the argument variable
                enter(WriteVariableExpressionTag.class, (e3, vw) -> {
                    vw.input(42);
                }).exit();

                enter(WriteVariableExpressionTag.class, (e3, vw) -> {
                    enter(LiteralExpressionTag.class).exit();
                    vw.input(assertJSFunctionInput);
                }).exit();

                // return statement
                enter(ReadVariableExpressionTag.class).exit();
            }).exit();
            // get function from the call
            call.input(assertJSFunctionInput);

            // in the bar function
            enter(EvalCallTag.class, (e2, eval) -> {
                enter(ReadVariableExpressionTag.class).exit();
                eval.input();
                enter(LiteralExpressionTag.class).exit();
                eval.input();

                enter(WriteVariableExpressionTag.class, (e3, vw) -> {
                    enter(LiteralExpressionTag.class).exit();
                    vw.input();
                }).exit();
            }).exit();

            // return a++

            // return statement
            enter(WriteVariableExpressionTag.class, (e2, vw) -> {
                enter(BinaryExpressionTag.class, (e3, b) -> {
                    enter(ReadVariableExpressionTag.class).exit();
                    b.input(30);
                    enter(LiteralExpressionTag.class).exit();
                    b.input(1);
                }).exit();
                vw.input(31);
            }).exit();
            // var a = 1 in the eval

        }).exit();
    }

    @Test
    public void evalReadWrite2() {
        evalAllTags("function foo(a) { \n" +
                        "  function bar() { \n" +
                        "    return a++; \n" +
                        "  } \n" +
                        "  eval('var a = 30'); \n" +
                        "  return bar;\n" +
                        "}\n" +
                        "foo(42)();");
        int cnt = 0;
        for (Event event : events) {
            System.out.println(cnt++ + ":" + event.debug());
        }

        enter(WritePropertyExpressionTag.class, (e, wp) -> {
            wp.input(assertGlobalObjectInput);
            enter(LiteralExpressionTag.class).exit((e2) -> {
                assertAttribute(e2, TYPE, LiteralExpressionTag.Type.FunctionLiteral.name());
            });
            wp.input(assertJSFunctionInput);
        }).exit();

        // foo(42)();
        enter(FunctionCallExpressionTag.class, (e1, call) -> {
            // fetch the target for the call (which is undefined)
            enter(LiteralExpressionTag.class).exit(assertReturnValue(Undefined.instance));
            call.input(assertUndefinedInput);

            enter(FunctionCallExpressionTag.class, (e2, call2) -> {
                enter(LiteralExpressionTag.class).exit(assertReturnValue(Undefined.instance));
                call.input(assertUndefinedInput);
                // get the function from the property foo
                enter(ReadPropertyExpressionTag.class, (e3, pr) -> {
                    assertAttribute(e3, KEY, "foo");
                    pr.input(assertGlobalObjectInput);
                }).exit();
                call.input(assertJSFunctionInput);
                enter(LiteralExpressionTag.class).exit();
                call.input(42);
                // in the foo function
                // write the 42 into the argument variable
                enter(WriteVariableExpressionTag.class, (e3, vw) -> {
                    vw.input(42);
                }).exit();

                enter(WriteVariableExpressionTag.class, (e3, vw) -> {
                    enter(LiteralExpressionTag.class).exit();
                    vw.input(assertJSFunctionInput);
                }).exit();

                // in the bar function
                enter(EvalCallTag.class, (e3, eval) -> {
                    enter(ReadVariableExpressionTag.class).exit();
                    eval.input();
                    enter(LiteralExpressionTag.class).exit();
                    eval.input();

                    enter(WriteVariableExpressionTag.class, (e4, vw) -> {
                        enter(LiteralExpressionTag.class).exit();
                        vw.input();
                    }).exit();
                }).exit();

                // return statement
                enter(ReadVariableExpressionTag.class).exit();
            }).exit();
            // get function from the call
            call.input(assertJSFunctionInput);

            // return a++
            enter(WriteVariableExpressionTag.class, (e2, vw) -> {
                enter(BinaryExpressionTag.class, (e3, b) -> {
                    enter(ReadVariableExpressionTag.class).exit();
                    b.input(30);
                    enter(LiteralExpressionTag.class).exit();
                    b.input(1);
                }).exit();
                vw.input(31);
            }).exit();

        }).exit();
    }

    @Test
    public void evalReadWrite3() {
        evalAllTags("function foo(a) { \n" +
                        "  function bar() { \n" +
                        "    a++; \n" +
                        "    return a; \n" +
                        "  } \n" +
                        "  eval('var a = 30'); \n" +
                        "  return bar;\n" +
                        "}\n" +
                        "foo(42)();");
        int cnt = 0;
        for (Event event : events) {
            System.out.println(cnt++ + ":" + event.debug());
        }

        enter(WritePropertyExpressionTag.class, (e, wp) -> {
            wp.input(assertGlobalObjectInput);
            enter(LiteralExpressionTag.class).exit((e2) -> {
                assertAttribute(e2, TYPE, LiteralExpressionTag.Type.FunctionLiteral.name());
            });
            wp.input(assertJSFunctionInput);
        }).exit();

        // foo(42)();
        enter(FunctionCallExpressionTag.class, (e1, call) -> {
            // fetch the target for the call (which is undefined)
            enter(LiteralExpressionTag.class).exit(assertReturnValue(Undefined.instance));
            call.input(assertUndefinedInput);

            enter(FunctionCallExpressionTag.class, (e2, call2) -> {
                enter(LiteralExpressionTag.class).exit(assertReturnValue(Undefined.instance));
                call.input(assertUndefinedInput);
                // get the function from the property foo
                enter(ReadPropertyExpressionTag.class, (e3, pr) -> {
                    assertAttribute(e3, KEY, "foo");
                    pr.input(assertGlobalObjectInput);
                }).exit();
                call.input(assertJSFunctionInput);
                enter(LiteralExpressionTag.class).exit();
                call.input(42);
                // in the foo function
                // write the 42 into the argument variable
                enter(WriteVariableExpressionTag.class, (e3, vw) -> {
                    vw.input(42);
                }).exit();

                enter(WriteVariableExpressionTag.class, (e3, vw) -> {
                    enter(LiteralExpressionTag.class).exit();
                    vw.input(assertJSFunctionInput);
                }).exit();

                // in the bar function
                enter(EvalCallTag.class, (e3, eval) -> {
                    enter(ReadVariableExpressionTag.class).exit();
                    eval.input();
                    enter(LiteralExpressionTag.class).exit();
                    eval.input();

                    enter(WriteVariableExpressionTag.class, (e4, vw) -> {
                        enter(LiteralExpressionTag.class).exit();
                        vw.input();
                    }).exit();
                }).exit();

                // return statement
                enter(ReadVariableExpressionTag.class).exit();
            }).exit();
            // get function from the call
            call.input(assertJSFunctionInput);

            // a++
            if (false) {
                enter(WriteVariableExpressionTag.class, (e2, vw) -> {
                    enter(BinaryExpressionTag.class, (e3, b) -> {
                        enter(ReadVariableExpressionTag.class).exit();
                        b.input(30);
                        enter(LiteralExpressionTag.class).exit();
                        b.input(1);
                    }).exit();
                    vw.input(31);
                }).exit();
            }

            // return a;
            // return statement
            enter(ReadVariableExpressionTag.class).exit();
        }).exit();
    }

    @Test
    public void evalReadWrite4() {
        evalAllTags("function foo(a) { \n" +
                        "  function bar() { \n" +
                        "    a = a+1; \n" +
                        "    return a; \n" +
                        "  } \n" +
                        "  eval('var a = 30'); \n" +
                        "  return bar;\n" +
                        "}\n" +
                        "foo(42)();");
        enter(WritePropertyExpressionTag.class, (e, wp) -> {
            wp.input(assertGlobalObjectInput);
            enter(LiteralExpressionTag.class).exit((e2) -> {
                assertAttribute(e2, TYPE, LiteralExpressionTag.Type.FunctionLiteral.name());
            });
            wp.input(assertJSFunctionInput);
        }).exit();

        // foo(42)();
        enter(FunctionCallExpressionTag.class, (e1, call) -> {
            // fetch the target for the call (which is undefined)
            enter(LiteralExpressionTag.class).exit(assertReturnValue(Undefined.instance));
            call.input(assertUndefinedInput);

            enter(FunctionCallExpressionTag.class, (e2, call2) -> {
                enter(LiteralExpressionTag.class).exit(assertReturnValue(Undefined.instance));
                call.input(assertUndefinedInput);
                // get the function from the property foo
                enter(ReadPropertyExpressionTag.class, (e3, pr) -> {
                    assertAttribute(e3, KEY, "foo");
                    pr.input(assertGlobalObjectInput);
                }).exit();
                call.input(assertJSFunctionInput);
                enter(LiteralExpressionTag.class).exit();
                call.input(42);
                // in the foo function
                // write the 42 into the argument variable
                enter(WriteVariableExpressionTag.class, (e3, vw) -> {
                    vw.input(42);
                }).exit();

                enter(WriteVariableExpressionTag.class, (e3, vw) -> {
                    enter(LiteralExpressionTag.class).exit();
                    vw.input(assertJSFunctionInput);
                }).exit();

                // in the bar function
                enter(EvalCallTag.class, (e3, eval) -> {
                    // enter(ReadVariableExpressionTag.class).exit();
                    eval.input();
                    enter(LiteralExpressionTag.class).exit();
                    eval.input();

                    enter(WriteVariableExpressionTag.class, (e4, vw) -> {
                        enter(LiteralExpressionTag.class).exit();
                        vw.input();
                    }).exit();
                }).exit();

                // return statement
                enter(ReadVariableExpressionTag.class).exit();
            }).exit();
            // get function from the call
            call.input(assertJSFunctionInput);

            // return a++
            enter(WriteVariableExpressionTag.class, (e2, vw) -> {
                enter(BinaryExpressionTag.class, (e3, b) -> {
                    enter(ReadVariableExpressionTag.class).exit();
                    b.input(30);
                    enter(LiteralExpressionTag.class).exit();
                    b.input(1);
                }).exit();
                vw.input(31);
            }).exit();

            enter(ReadVariableExpressionTag.class).exit();
        }).exit();
    }
}
