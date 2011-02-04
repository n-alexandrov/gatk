/*
 * Copyright (c) 2010 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package org.broadinstitute.sting.gatk.walkers.annotator;

import org.broadinstitute.sting.gatk.walkers.annotator.interfaces.GenotypeAnnotation;
import org.broadinstitute.sting.gatk.walkers.annotator.interfaces.StandardAnnotation;
import org.broadinstitute.sting.gatk.refdata.RefMetaDataTracker;
import org.broadinstitute.sting.gatk.contexts.ReferenceContext;
import org.broadinstitute.sting.gatk.contexts.StratifiedAlignmentContext;
import org.broadinstitute.sting.utils.pileup.ReadBackedPileup;
import org.broadinstitute.sting.utils.pileup.PileupElement;
import org.broadinstitute.sting.utils.pileup.ReadBackedExtendedEventPileup;
import org.broadinstitute.sting.utils.pileup.ExtendedEventPileupElement;
import org.broad.tribble.util.variantcontext.VariantContext;
import org.broad.tribble.util.variantcontext.Genotype;
import org.broad.tribble.util.variantcontext.Allele;
import org.broad.tribble.vcf.VCFFormatHeaderLine;
import org.broad.tribble.vcf.VCFCompoundHeaderLine;
import org.broad.tribble.vcf.VCFHeaderLineType;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 * User: asivache
 * Date: Feb 4, 2011
 * Time: 3:59:27 PM
 * To change this template use File | Settings | File Templates.
 */
public class DepthAndFractionBySample implements GenotypeAnnotation {

        private static String REF_ALLELE = "REF";

        private static String DEL = "DEL"; // constant, for speed: no need to create a key string for deletion allele every time

        public Map<String, Object> annotate(RefMetaDataTracker tracker, ReferenceContext ref,
                                            StratifiedAlignmentContext stratifiedContext, VariantContext vc, Genotype g) {
            if ( g == null || !g.isCalled() )
                return null;

            if ( vc.isSNP() )
                return annotateSNP(stratifiedContext, vc);
            if ( vc.isIndel() )
                return annotateIndel(stratifiedContext, vc);

            return null;
        }

        private Map<String,Object> annotateSNP(StratifiedAlignmentContext stratifiedContext, VariantContext vc) {

            if ( ! stratifiedContext.getContext(StratifiedAlignmentContext.StratifiedContextType.COMPLETE).hasBasePileup() ) return null;

            HashMap<Byte, Integer> alleleCounts = new HashMap<Byte, Integer>();
            for ( Allele allele : vc.getAlternateAlleles() )
                alleleCounts.put(allele.getBases()[0], 0);

            ReadBackedPileup pileup = stratifiedContext.getContext(StratifiedAlignmentContext.StratifiedContextType.COMPLETE).getBasePileup();
            int totalDepth = pileup.size();

            Map<String, Object> map = new HashMap<String, Object>();
            map.put(getKeyNames().get(0), totalDepth); // put total depth in right away

            if ( totalDepth == 0 ) return map; // done, can not compute FA at 0 coverage!!

            for ( PileupElement p : pileup ) {
                if ( alleleCounts.containsKey(p.getBase()) ) // it's an alt
                    alleleCounts.put(p.getBase(), alleleCounts.get(p.getBase())+1);
            }

            // we need to add counts in the correct order
            String[] fracs = new String[alleleCounts.size()];
            for (int i = 0; i < vc.getAlternateAlleles().size(); i++) {
                fracs[i] = String.format("%.3f", ((float)alleleCounts.get(vc.getAlternateAllele(i).getBases()[0]))/totalDepth);
            }

            map.put(getKeyNames().get(1), fracs);
            return map;
        }

        private Map<String,Object> annotateIndel(StratifiedAlignmentContext
            stratifiedContext, VariantContext
            vc) {

            if ( ! stratifiedContext.getContext(StratifiedAlignmentContext.StratifiedContextType.COMPLETE).hasExtendedEventPileup() ) {
                return null;
            }

            ReadBackedExtendedEventPileup pileup = stratifiedContext.getContext(StratifiedAlignmentContext.StratifiedContextType.COMPLETE).getExtendedEventPileup();
            //ReadBackedPileup pileup = stratifiedContext.getContext(StratifiedAlignmentContext.StratifiedContextType.COMPLETE).getBasePileup();
            if ( pileup == null )
                return null;
            int totalDepth = pileup.size();

            Map<String, Object> map = new HashMap<String, Object>();
            map.put(getKeyNames().get(0), totalDepth); // put total depth in right away

            if ( totalDepth == 0 ) return map;

            HashMap<String, Integer> alleleCounts = new HashMap<String, Integer>();
            Allele refAllele = vc.getReference();

            for ( Allele allele : vc.getAlternateAlleles() ) {

                if ( allele.isNoCall() ) {
                    continue; // this does not look so good, should we die???
                }

                alleleCounts.put(getAlleleRepresentation(allele), 0);
            }

            for ( ExtendedEventPileupElement e : pileup.toExtendedIterable() ) {
                if ( e.isInsertion() ) {

                    final String b =  e.getEventBases();
                    if ( alleleCounts.containsKey(b) ) {
                        alleleCounts.put(b, alleleCounts.get(b)+1);
                    }

                } else {
                    if ( e.isDeletion() ) {
                        if ( e.getEventLength() == refAllele.length() ) {
                            // this is indeed the deletion allele recorded in VC
                            final String b = DEL;
                            if ( alleleCounts.containsKey(b) ) {
                                alleleCounts.put(b, alleleCounts.get(b)+1);
                            }
                        }
//                    else {
//                        System.out.print("   deletion of WRONG length found");
//                    }
                    }
                }
            }

            String[] fracs = new String[alleleCounts.size()];
            for (int i = 0; i < vc.getAlternateAlleles().size(); i++)
                fracs[i] = String.format("%.3f", ((float)alleleCounts.get(getAlleleRepresentation(vc.getAlternateAllele(i))))/totalDepth);

            map.put(getKeyNames().get(1), fracs);

            //map.put(getKeyNames().get(0), counts);
            return map;
        }

        private String getAlleleRepresentation(Allele allele) {
            if ( allele.isNull() ) { // deletion wrt the ref
                 return DEL;
            } else { // insertion, pass actual bases
                return allele.getBaseString();
            }

        }

     //   public String getIndelBases()
        public List<String> getKeyNames() { return Arrays.asList("DP","FA"); }

        public List<VCFFormatHeaderLine> getDescriptions() {
            return Arrays.asList(new VCFFormatHeaderLine(getKeyNames().get(0),
                            VCFCompoundHeaderLine.UNBOUNDED,
                            VCFHeaderLineType.Integer,
                            "Total read depth per sample"),
                    new VCFFormatHeaderLine(getKeyNames().get(1),
                            VCFCompoundHeaderLine.UNBOUNDED,
                            VCFHeaderLineType.Float,
                            "Fractions of reads supporting each reported alternative allele, per sample"));
        }
}
