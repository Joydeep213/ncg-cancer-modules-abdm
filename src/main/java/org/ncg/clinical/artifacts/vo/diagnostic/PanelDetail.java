package org.ncg.clinical.artifacts.vo.diagnostic;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PanelDetail extends AttachmentDetail {
	private List<TestDetail> tests;
}