package org.metrics.defectlab.analysis.promise.bytecode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ArrayInstruction;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.CHECKCAST;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.FieldInstruction;
import org.apache.bcel.generic.GETFIELD;
import org.apache.bcel.generic.GETSTATIC;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.INSTANCEOF;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.LocalVariableInstruction;
import org.apache.bcel.generic.InvokeInstruction;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.PUTFIELD;
import org.apache.bcel.generic.PUTSTATIC;
import org.apache.bcel.generic.ReferenceType;
import org.apache.bcel.generic.ReturnInstruction;
import org.apache.bcel.generic.Type;
import org.apache.bcel.generic.TypedInstruction;

/**
 * Turns compiled classes into a {@link BytecodeProjectModel}.
 *
 * <p>Each class file is parsed exactly once and every fact a PROMISE metric
 * needs is recorded on the model, so no calculator re-scans bytecode.
 */
public final class BytecodeProjectAnalyzer {

    private final List<String> diagnostics = new ArrayList<>();

    public List<String> getDiagnostics() {
        return List.copyOf(diagnostics);
    }

    /**
     * @param classFiles compiled classes belonging to the analysed release
     * @param projectClassNames classes that may produce metric rows
     */
    public BytecodeProjectModel analyze(
            Collection<Path> classFiles, Set<String> projectClassNames) {
        return analyze(classFiles, projectClassNames, List.of());
    }

    /**
     * @param classFiles compiled classes belonging to the analysed release
     * @param projectClassNames classes that may produce metric rows
     * @param classpathJars dependency jars used to compile the release, used
     *         to resolve ancestor classes that live outside the project (see
     *         {@link #resolveExternalAncestors})
     */
    public BytecodeProjectModel analyze(
            Collection<Path> classFiles, Set<String> projectClassNames,
            List<Path> classpathJars) {
        BytecodeProjectModel model = new BytecodeProjectModel(projectClassNames);
        for (Path classFile : classFiles) {
            try (InputStream stream = Files.newInputStream(classFile)) {
                JavaClass javaClass = new ClassParser(
                        stream, classFile.toString()).parse();
                model.add(readClass(javaClass));
            } catch (IOException | RuntimeException exception) {
                diagnostics.add("Could not read compiled class " + classFile
                        + ": " + exception.getMessage());
            }
        }
        resolveExternalAncestors(model, classpathJars);
        model.freeze();
        return model;
    }

    /**
     * Follows every project class's superclass chain past the project's own
     * boundary, resolving JDK and dependency-jar ancestors so DIT, MFA, IC and
     * CBM see the true hierarchy the way CKJM's classpath-backed
     * {@code JavaClass.getSuperClasses()} does. A class that cannot be
     * resolved (a missing dependency) simply ends the walk there, same as if
     * this step did not run.
     */
    private void resolveExternalAncestors(
            BytecodeProjectModel model, List<Path> classpathJars) {
        ExternalAncestorResolver resolver = new ExternalAncestorResolver(classpathJars);
        Deque<String> pending = new ArrayDeque<>();
        for (BytecodeClassModel type : new ArrayList<>(model.getClasses())) {
            if (type.getSuperclass() != null) {
                pending.push(type.getSuperclass());
            }
        }

        Set<String> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            String name = pending.pop();
            if (name == null || "java.lang.Object".equals(name) || !visited.add(name)
                    || model.isAnalyzedClass(name)) {
                continue;
            }
            resolver.resolve(name).ifPresent(javaClass -> {
                BytecodeClassModel external = readClass(javaClass);
                model.add(external);
                if (external.getSuperclass() != null) {
                    pending.push(external.getSuperclass());
                }
            });
        }
    }

    private BytecodeClassModel readClass(JavaClass javaClass) {
        String fqn = javaClass.getClassName();
        String superclass = javaClass.getSuperclassName();
        if ("java.lang.Object".equals(fqn) || fqn.equals(superclass)) {
            superclass = null;
        }

        BytecodeClassModel model = new BytecodeClassModel(
                fqn,
                javaClass.getPackageName(),
                superclass,
                List.of(javaClass.getInterfaceNames()),
                javaClass.isInterface(),
                fqn.contains("$"));

        // Inheritance is a coupling channel in CKJM.
        addType(model.referencedTypes, superclass);
        for (String implemented : javaClass.getInterfaceNames()) {
            addType(model.referencedTypes, implemented);
        }

        ConstantPoolGen pool = new ConstantPoolGen(javaClass.getConstantPool());
        for (Field field : javaClass.getFields()) {
            model.fields.add(readField(fqn, field, model));
        }
        for (Method method : javaClass.getMethods()) {
            model.methods.add(readMethod(fqn, method, pool));
        }
        return model;
    }

    private BytecodeFieldModel readField(
            String owner, Field field, BytecodeClassModel classModel) {
        String type = describe(field.getType());
        addType(classModel.referencedTypes, type);
        return new BytecodeFieldModel(
                new FieldRef(owner, field.getName()),
                type,
                field.isPrivate(),
                field.isProtected(),
                field.isStatic());
    }

    private BytecodeMethodModel readMethod(
            String owner, Method method, ConstantPoolGen pool) {

        List<String> argumentTypes = new ArrayList<>();
        for (Type argument : method.getArgumentTypes()) {
            argumentTypes.add(describe(argument));
        }
        List<String> declaredExceptions = method.getExceptionTable() == null
                ? List.of()
                : List.of(method.getExceptionTable().getExceptionNames());

        InstructionList instructions = null;
        if (method.getCode() != null) {
            MethodGen generator = new MethodGen(method, owner, pool);
            instructions = generator.getInstructionList();
        }

        BytecodeMethodModel model = new BytecodeMethodModel(
                new MethodRef(owner, method.getName(), argumentTypes),
                argumentTypes,
                describe(method.getReturnType()),
                declaredExceptions,
                method.isPublic(),
                method.isStatic(),
                method.isAbstract(),
                method.isNative(),
                instructions == null ? 0 : instructions.getLength(),
                CyclomaticComplexityReader.complexityOf(
                        instructions, !declaredExceptions.isEmpty(),
                        "<init>".equals(method.getName())
                                || "<clinit>".equals(method.getName())));

        // Signature-level coupling: parameters, return type and throws clause.
        argumentTypes.forEach(type -> addType(model.referencedTypes, type));
        addType(model.referencedTypes, describe(method.getReturnType()));
        declaredExceptions.forEach(type -> addType(model.referencedTypes, type));
        method.getCode();

        if (instructions != null) {
            readInstructions(model, instructions, pool);
        }
        readCatchTypes(model, method);
        return model;
    }

    private void readInstructions(
            BytecodeMethodModel model, InstructionList instructions, ConstantPoolGen pool) {

        for (InstructionHandle handle : instructions) {
            Instruction instruction = handle.getInstruction();

            if (instruction instanceof InvokeInstruction) {
                readInvocation(model, (InvokeInstruction) instruction, pool);
            } else if (instruction instanceof FieldInstruction) {
                readFieldAccess(model, (FieldInstruction) instruction, pool);
            } else if (instruction instanceof CHECKCAST
                    || instruction instanceof INSTANCEOF
                    || instruction instanceof ArrayInstruction
                    || instruction instanceof LocalVariableInstruction
                    || instruction instanceof ReturnInstruction) {
                // Exactly the operand types CKJM's MethodVisitor registers.
                // Object allocation (new, anewarray, multianewarray) is
                // deliberately NOT among them: CKJM has no visitor for those,
                // so counting them here would invent coupling the published
                // PROMISE CBO/Ce values do not contain.
                try {
                    addType(model.referencedTypes,
                            describe(((TypedInstruction) instruction).getType(pool)));
                } catch (RuntimeException ignored) {
                    // An unresolvable operand is not a coupling we can record.
                }
            }
        }
    }

    private void readInvocation(
            BytecodeMethodModel model, InvokeInstruction invoke, ConstantPoolGen pool) {

        String declaringClass = describe(invoke.getReferenceType(pool));
        List<String> argumentTypes = new ArrayList<>();
        for (Type argument : invoke.getArgumentTypes(pool)) {
            argumentTypes.add(describe(argument));
        }

        // RFC keeps JDK calls; the coupling graph filters them out later.
        model.invokedMethods.add(new MethodRef(
                declaringClass, invoke.getMethodName(pool), argumentTypes));

        addType(model.referencedTypes, declaringClass);
        addType(model.referencedTypes, describe(invoke.getReturnType(pool)));
        argumentTypes.forEach(type -> addType(model.referencedTypes, type));
    }

    private void readFieldAccess(
            BytecodeMethodModel model, FieldInstruction access, ConstantPoolGen pool) {

        String declaringClass = describe(access.getReferenceType(pool));
        FieldRef ref = new FieldRef(declaringClass, access.getFieldName(pool));
        if (access instanceof GETFIELD || access instanceof GETSTATIC) {
            model.fieldReads.add(ref);
        } else if (access instanceof PUTFIELD || access instanceof PUTSTATIC) {
            model.fieldWrites.add(ref);
        }
        addType(model.referencedTypes, declaringClass);
        addType(model.referencedTypes, describe(access.getFieldType(pool)));
    }

    private void readCatchTypes(BytecodeMethodModel model, Method method) {
        if (method.getCode() == null || method.getCode().getExceptionTable() == null) {
            return;
        }
        for (var entry : method.getCode().getExceptionTable()) {
            var caught = entry.getCatchType() == 0
                    ? null
                    : method.getConstantPool().getConstantString(
                            entry.getCatchType(), org.apache.bcel.Const.CONSTANT_Class);
            if (caught != null) {
                addType(model.referencedTypes, caught.replace('/', '.'));
            }
        }
    }

    /**
     * Reduces a BCEL type to a class name, unwrapping arrays so that
     * {@code Node[]} reads as {@code Node}. Primitive names are kept because CAM
     * groups parameters by type and MOA must recognise them.
     *
     * <p>{@code Type.NULL} and {@code Type.UNKNOWN} are stack pseudo-types, not
     * classes: {@code aconst_null} would otherwise surface as a coupled type
     * literally named {@code <null object>}.
     */
    private String describe(Type type) {
        if (type instanceof ArrayType) {
            return describe(((ArrayType) type).getBasicType());
        }
        if (type instanceof ObjectType) {
            return ((ObjectType) type).getClassName();
        }
        if (type instanceof ReferenceType) {
            return "";
        }
        return type == null ? "" : type.toString();
    }

    private void addType(Set<String> target, String type) {
        if (type != null && !type.isEmpty() && !JdkClassPolicy.isPrimitive(type)) {
            target.add(type);
        }
    }
}
