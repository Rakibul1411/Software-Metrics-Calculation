package org.metrics.defectlab.analysis.promise.calculator;

import java.util.LinkedHashSet;
import java.util.Set;

import org.metrics.defectlab.analysis.promise.bytecode.BytecodeClassModel;
import org.metrics.defectlab.analysis.promise.bytecode.BytecodeMethodModel;
import org.metrics.defectlab.analysis.promise.bytecode.BytecodeProjectModel;
import org.metrics.defectlab.analysis.promise.bytecode.MethodRef;

/** CBO, Ca, Ce and RFC. */
public final class CouplingCalculators {

    private CouplingCalculators() {
    }

    /** CBO: distinct classes coupled in either direction. */
    public static int cbo(BytecodeClassModel type, BytecodeProjectModel project) {
        return project.getCouplingGraph().coupledWith(type.getFqn()).size();
    }

    /** Ca: classes that depend upon this one. */
    public static int ca(BytecodeClassModel type, BytecodeProjectModel project) {
        return project.getCouplingGraph().incomingTo(type.getFqn()).size();
    }

    /** Ce: classes this one depends upon. */
    public static int ce(BytecodeClassModel type, BytecodeProjectModel project) {
        return project.getCouplingGraph().outgoingFrom(type.getFqn()).size();
    }

    /**
     * RFC: the class's own methods plus every distinct method invoked directly
     * from their bodies, stopping at the first call level.
     *
     * <p>Unlike the coupling metrics, RFC keeps calls into the JDK: they are
     * still part of the response set.
     */
    public static int rfc(BytecodeClassModel type) {
        Set<MethodRef> responseSet = new LinkedHashSet<>();
        for (BytecodeMethodModel method : type.getMethods()) {
            responseSet.add(method.getRef());
            responseSet.addAll(method.getInvokedMethods());
        }
        return responseSet.size();
    }
}
