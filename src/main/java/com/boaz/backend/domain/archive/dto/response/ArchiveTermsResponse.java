package com.boaz.backend.domain.archive.dto.response;

import java.util.List;

public record ArchiveTermsResponse(List<TermItem> terms) {

    public record TermItem(int value, String label) {
        public static TermItem from(int term) {
            String label = (term == 0) ? "7기 이전" : term + "기";
            return new TermItem(term, label);
        }
    }

    public static ArchiveTermsResponse from(List<Integer> terms) {
        return new ArchiveTermsResponse( 
            terms.stream().map(TermItem::from).toList()
        );
    }
}