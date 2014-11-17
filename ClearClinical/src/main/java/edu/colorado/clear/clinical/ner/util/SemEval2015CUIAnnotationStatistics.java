package edu.colorado.clear.clinical.ner.util;

import java.util.Collection;
import java.util.HashSet;

import org.cleartk.semeval2015.type.DisorderSpan;
/**
 * This class should output annotation statistics on how well the pipeline maps CUIs
 * @author ozborn
 *
 */
public class SemEval2015CUIAnnotationStatistics {
	double total_correct_count = 0, total_wrong_count=0;
	
	
	public void add(Collection<DisorderSpan> gold, Collection<DisorderSpan> predicted){
		int correct_count = 0, wrong_count=0;
		System.out.println("Gold/Predicted CUI Mapping size:"+gold.size()+" / "+predicted.size());
		HashSet<DisorderSpan> unmatched_golds = new HashSet<DisorderSpan>();
		HashSet<DisorderSpan> unmatched_predictions = new HashSet<DisorderSpan>();
		for(DisorderSpan g : gold){
			boolean matching_span_found = false;
			for (DisorderSpan p : predicted){
				if(g.getBegin()==p.getBegin() && g.getEnd()==p.getEnd()){
					matching_span_found = true;
					if(g.getCui().equals(p.getCui())) {
						correct_count++;
						//System.out.println("Predicted CUI:"+p.getCui()+" matches gold Cui:"+g.getCui()+" for pred text:"+p.getCoveredText());
						break;
					} else {
						wrong_count++;
						//System.out.println(" Predicted CUI:"+p.getCui()+" fails to match gold Cui:"+g.getCui()+" for pred text:"+p.getCoveredText());
						break;
					}
					
				}
			}
			if(!matching_span_found) unmatched_golds.add(g);
		}
		total_wrong_count = total_wrong_count+wrong_count;
		total_correct_count = total_correct_count+correct_count;
		
		for (DisorderSpan p : predicted){
			boolean matching_span_found = false;
			for(DisorderSpan g : gold){
				if(g.getBegin()==p.getBegin() && g.getEnd()==p.getEnd()){
					matching_span_found = true;
				}
			}
			if(!matching_span_found) unmatched_predictions.add(p);
		}
		
		System.out.println("Correct Count:"+correct_count+" Wrong Count:"+wrong_count);
		System.out.println("Total Correct Count:"+total_correct_count+" Total Wrong Count:"+total_wrong_count+
				"CUI Prediction Accuracy:"+(total_correct_count/(total_correct_count+total_wrong_count)));
		for(DisorderSpan unmatched_pred : unmatched_predictions){
			System.out.println("Unmatched Prediction:"+unmatched_pred.getCoveredText()+" ("+
			unmatched_pred.getBegin()+"/"+unmatched_pred.getEnd()+") with cui:"+unmatched_pred.getCui());
		}
		for(DisorderSpan unmatched_gold : unmatched_golds){
			System.out.println("Unmatched Gold:"+unmatched_gold.getCoveredText()+" ("+
			unmatched_gold.getBegin()+"/"+unmatched_gold.getEnd()+") with cui:"+unmatched_gold.getCui());
		}

		
	}

}
