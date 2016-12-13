package fr.univ_lille1.m2iagl.dd;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.mdkt.compiler.InMemoryJavaCompiler;
import org.omg.CosNaming.IstringHelper;

import fr.univ_lille1.m2iagl.challenge.Challenge;
import fr.univ_lille1.m2iagl.spoon.processor.AssignementProcessor;
import fr.univ_lille1.m2iagl.spoon.processor.VariableProcessor;
import fr.univ_lille1.m2iagl.spoon.templatechallenge.ITemplateChallenge;
import spoon.Launcher;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.NameFilter;
import spoon.reflect.visitor.filter.TypeFilter;

public class DeltaDebug {
	public static final String CHALLENGE_FILE = "src/fr/univ_lille1/m2iagl/spoon/templatechallenge/TemplateChallenge.java";
	public static final String CHALLENGE_NAME = "TemplateChallenge";

	public static <T> boolean generateCauseEffectChain(T input, String challengeFile, String challengeName) {
		CauseEffectChainSingleton.getInstance().resetCauseEffectChain();

		// Create a TemplateChallenge launcher
		Launcher l = new Launcher();
		l.setArgs(new String[] {"-i", challengeFile});
		l.buildModel();

		CtClass foo = (CtClass) l.getFactory().Package().getRootPackage()
				.getElements(new NameFilter(challengeName)).get(0);

		CtMethod cChallenge = (CtMethod) foo.getElements(new TypeFilter(CtMethod.class)).get(0);

		// Apply transformations
		cChallenge = AssignementProcessor.transform(cChallenge);
		cChallenge = VariableProcessor.transform(cChallenge);

		// Create a new instance
		Class tCClass = null;
		ITemplateChallenge tC = null;
		try {
			tCClass = InMemoryJavaCompiler.compile(foo.getQualifiedName(),
					"package " + foo.getPackage().getQualifiedName() + ";" + foo.toString());
			tC = (ITemplateChallenge) tCClass.newInstance();
		} catch (Exception e1) {
			e1.printStackTrace();
		}

		// Run to check success
		boolean hasSucceded = true;
		try {
			tC.challenge(input);
		} catch (Exception e1) {
			hasSucceded = false;
		}

		return hasSucceded;
	}

	public static <T> CauseEffectChain ddmin(T inputFail, T inputSuccess, Challenge<T> c) {
		CauseEffectChainImpl cECFail = new CauseEffectChainImpl();
		CauseEffectChainImpl cECSuccess = new CauseEffectChainImpl();

		generateCauseEffectChain(inputFail, CHALLENGE_FILE, CHALLENGE_NAME);
		cECFail.setChain(CauseEffectChainSingleton.getInstance().getCauseEffectChain().getChain());
		generateCauseEffectChain(inputSuccess, CHALLENGE_FILE, CHALLENGE_NAME);
		cECSuccess.setChain(CauseEffectChainSingleton.getInstance().getCauseEffectChain().getChain());
		List<ChainElement> cEsReturn = null;

		System.out.println("==== Chaine d'execution Input Success ====");
		((CauseEffectChainImpl) cECSuccess).print();

		System.out.println("\r\n==== Chaine d'execution Input Fail ====");
		((CauseEffectChainImpl) cECFail).print();

		System.out.println("\r\n==== Chaine de débogage ====");

		int n = 2;

		cEsReturn = difference(cECSuccess.getChain(), cECFail.getChain());
		while (cEsReturn.size() >= 2) {
			int start = 0;
			int subset_lenght = cEsReturn.size() / n;
			boolean some_complement_is_failing = false;

			while (start < cEsReturn.size()) {
				List<ChainElement> complement = new ArrayList<ChainElement>();
				complement.addAll(cEsReturn.subList(0, start));
				complement.addAll(cEsReturn.subList(start + subset_lenght, cEsReturn.size()));

				CauseEffectChainSingleton.getInstance().getDiffCauseEffectChain().clearChainElements();
				CauseEffectChainImpl cDiff = CauseEffectChainSingleton.getInstance().getDiffCauseEffectChain();
				cDiff.setChain(complement);

				if (generateCauseEffectChain(inputFail, CHALLENGE_FILE, CHALLENGE_NAME)) {
					cEsReturn.clear();
					cEsReturn.addAll(complement);
					n = Math.max(n - 1, 2);
					some_complement_is_failing = true;
					break;
				}

				start += subset_lenght;
			}

			if (!some_complement_is_failing) {
				if (n == cEsReturn.size()) {
					break;
				}

				n = Math.min(n * 2, cEsReturn.size());
			}
		}
		

		return new CauseEffectChainImpl(cEsReturn);

	}

	public static List<ChainElement> difference(List<ChainElement> successChain, List<ChainElement> failChain) {
		List<ChainElement> result = new ArrayList<ChainElement>();
		
		List<Integer> li = new ArrayList<Integer>();

		for (int i = 0; i < successChain.size(); i++) {
			for (int j = 0; j < failChain.size(); j++) {
				ChainElementImpl successElement = (ChainElementImpl)successChain.get(i);
				ChainElementImpl failElement = (ChainElementImpl)failChain.get(j);
				
				/*
				if ((successElement.getLine().equals(failElement.getLine()) 
						&& successElement.getVariable().equals(failElement.getVariable()) 
						&& successElement.getIteration() == failElement.getIteration()
						&& (!(successElement.getValue() == null && failElement.getValue() == null)
						|| !(successElement.getValue() != null && failElement.getValue() != null
						   && !successElement.getValue().equals(failElement.getValue()))
						))) {
					result.add(failElement);
				}*/
				boolean isLastFailElement = (j == (failChain.size() - 1));
				if(isTheSameContextElements(successElement, failElement, isLastFailElement) && !isTheSameValue(successElement, failElement, isLastFailElement)) {
					result.add(failElement);
				}
			}
		}

		return result;
	}
	
	private static boolean isTheSameContextElements(ChainElementImpl successChain, ChainElementImpl failChain, boolean isLastFailElement) {
		boolean iterationCheck;
		
		if(isLastFailElement) {
			iterationCheck = (failChain.getIteration() == successChain.getIteration()) || (failChain.getIteration() == (successChain.getIteration() - 1));
		} else {
			iterationCheck = failChain.getIteration() == successChain.getIteration();
		}
		return successChain.getLine().equals(failChain.getLine()) && successChain.getVariable().equals(failChain.getVariable()) && iterationCheck;
	}
	
	private static boolean isTheSameValue(ChainElementImpl successChain, ChainElementImpl failChain, boolean isLastFailElement) {		
		if(successChain.getValue() == null && failChain.getValue() == null) {
			return true;
		}
		
		if(successChain.getValue() == null && failChain.getValue() != null) {
			return false;
		}
		
		if(successChain.getValue() != null && failChain.getValue() == null) {
			return false;
		}
		
		return successChain.getValue().equals(failChain.getValue());
	}

}
