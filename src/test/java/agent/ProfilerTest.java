package agent;

import org.junit.Test;
import org.objectweb.asm.*;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import static org.objectweb.asm.Opcodes.*;

/**
 * Profiling test class.
 *
 * @author Tudor
 */
public class ProfilerTest {
    @Test
    public void testAgent() {
        try {
            Tools.loadAgentLibrary(); // Load attach library
            AgentLoader.attachAgentToJVM(Tools.getCurrentPID(), Agent.class, AgentLoader.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        test(); // This should get logged, if everything worked
    }

    public void test() {
    }

    /**
     * Primary class used by tests to allow for method profiling information. Client
     * code will interact with this method only. This class is NOT thread safe.
     *
     * @author Tudor
     */
    public static class Agent implements ClassFileTransformer {

        private static Instrumentation instrumentation = null;
        private static Agent transformer;

        public static void agentmain(String string, Instrumentation instrument) {
            System.out.println("Agent loaded!");

            // initialization code:
            transformer = new Agent();
            instrumentation = instrument;
            instrumentation.addTransformer(transformer);

            try {
                instrumentation.redefineClasses(new ClassDefinition(ProfilerTest.class, Tools.getBytesFromClass(ProfilerTest.class)));
            } catch (Exception e) {
                System.out.println("Failed to redefine class!");
            }
        }

        /**
         * Kills this agent
         */
        public static void killAgent() {
            instrumentation.removeTransformer(transformer);
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class classBeingRedefined, ProtectionDomain protectionDomain, byte[] classBuffer)
                throws IllegalClassFormatException {

            System.out.println("Instrumenting class: " + className);

            // We can only profile classes that we can see. If a class uses a custom
            // ClassLoader we will nto be able to see it and crash if we try to
            // profile it.
            if (loader != ClassLoader.getSystemClassLoader()) {
                return classBuffer;
            }

            // Don't profile yourself, otherwise you'll die in a StackOverflow.
            if (className.startsWith("agent/ProfilerTest")) {
                return classBuffer;
            }

            byte[] result = classBuffer;
            try {
                // Create class reader from buffer
                ClassReader reader = new ClassReader(classBuffer);
                // Make writer
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                ClassAdapter profiler = new ProfileClassAdapter(writer, className);
                // Add the class adapter as a modifier
                reader.accept(profiler, 0);
                result = writer.toByteArray();
                System.out.println("Returning reinstrumented class: " + className);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return result;
        }

        /**
         * Base profiling class.
         */
        public static class Profiler {

            public static void start(String className, String methodName) {
                System.out.println(className + "\t" + methodName + "\tstart\t" + System.currentTimeMillis());
            }

            public static void end(String className, String methodName) {
                System.out.println(className + "\t" + methodName + "\tend\t" + System.currentTimeMillis());
            }
        }

        /**
         * Profiling class adapter.
         */
        public class ProfileClassAdapter extends ClassAdapter {

            private String className;

            public ProfileClassAdapter(ClassVisitor visitor, String className) {
                super(visitor);
                this.className = className;
            }

            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                return new ProfileMethodAdapter(super.visitMethod(access, name, desc, signature, exceptions), className, name);
            }
        }

        // The method adapter
        public class ProfileMethodAdapter extends MethodAdapter {
            private String className, methodName;

            public ProfileMethodAdapter(MethodVisitor visitor, String className, String methodName) {
                super(visitor);
                this.className = className;
                this.methodName = methodName;
                System.out.println("Profiled " + methodName + " in class " + className + ".");
            }

            public void visitCode() {
                // Push values into stack, then invoke the profile function
                this.visitLdcInsn(className);
                this.visitLdcInsn(methodName);
                this.visitMethodInsn(INVOKESTATIC,
                        "agent/ProfilerTest$Agent$Profiler",
                        "start",
                        "(Ljava/lang/String;Ljava/lang/String;)V");
                super.visitCode();
            }

            public void visitInsn(int inst) {
                switch (inst) {
                    // Match all return codes
                    case ARETURN:
                    case DRETURN:
                    case FRETURN:
                    case IRETURN:
                    case LRETURN:
                    case RETURN:
                    case ATHROW:
                        this.visitLdcInsn(className);
                        this.visitLdcInsn(methodName);
                        this.visitMethodInsn(INVOKESTATIC,
                                "agent/ProfilerTest$Agent$Profiler",
                                "end",
                                "(Ljava/lang/String;Ljava/lang/String;)V");
                    default:
                        break;
                }
                // Visit the actual function
                super.visitInsn(inst);
            }
        }
    }
}
