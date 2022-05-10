package nfuzzer.instrumentor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

// 调用 MethodTransformer
public class ClassTransformer extends ClassVisitor {

	private final String shmPath;
	private static final Logger logger = LogManager.getLogger(ClassTransformer.class);

	public ClassTransformer(ClassVisitor cv, String shmPath) {
		super(Opcodes.ASM5, cv);
		this.shmPath = shmPath;
	}

	@Override
	public MethodVisitor visitMethod(
		int access, 
		String name,
		String desc, 
		String signature, 
		String[] exceptions) {
		
		MethodVisitor mv;
		mv = cv.visitMethod(access, name, desc, signature, exceptions);

		if (mv != null) {
			logger.debug("visit method: " + name);
			mv = new MethodTransformer(mv, shmPath);
		}
		return mv;
	}
}
