/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hop.pipeline.transforms.formula;

import static org.apache.hop.pipeline.transforms.formula.util.FormulaFieldsExtractor.getFormulaFieldList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import org.apache.hop.core.exception.HopValueException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBigNumber;
import org.apache.hop.core.row.value.ValueMetaBoolean;
import org.apache.hop.core.row.value.ValueMetaDate;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.row.value.ValueMetaTimestamp;
import org.apache.hop.core.variables.Variables;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CompiledFormula;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.LightCellValue;
import org.apache.poi.ss.usermodel.StandaloneFormulaEngine;
import org.apache.poi.ss.usermodel.StandaloneFormulaEvaluator;
import org.apache.poi.ss.util.CellReference;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the workbook-free formula evaluation used by the formula transform:
 * field binding, variable resolution, replacement fields and error semantics.
 */
class FormulaEngineEvaluationTest {

  @Test
  void numberFieldGreaterThanLiteral() throws Exception {
    LightCellValue result =
        evaluate(List.of(field(new ValueMetaNumber("amount"), 150.0)), "[amount] > 100", false);

    assertEquals(CellType.BOOLEAN, result.getCellType());
    assertTrue(result.getBooleanValue());
  }

  @Test
  void stringFieldEqualsLiteral() throws Exception {
    LightCellValue result =
        evaluate(
            List.of(field(new ValueMetaString("status"), "active")),
            "[status] = \"active\"",
            false);

    assertEquals(CellType.BOOLEAN, result.getCellType());
    assertTrue(result.getBooleanValue());
  }

  @Test
  void booleanFieldIsTrue() throws Exception {
    LightCellValue result =
        evaluate(
            List.of(field(new ValueMetaBoolean("flag"), Boolean.TRUE)), "[flag] = TRUE", false);

    assertEquals(CellType.BOOLEAN, result.getCellType());
    assertTrue(result.getBooleanValue());
  }

  @Test
  void bigNumberFieldGreaterThanLiteral() throws Exception {
    LightCellValue result =
        evaluate(
            List.of(field(new ValueMetaBigNumber("amount"), new BigDecimal("200.5"))),
            "[amount] > 100",
            false);

    assertEquals(CellType.BOOLEAN, result.getCellType());
    assertTrue(result.getBooleanValue());
  }

  @Test
  void nullFieldIsBlankWhenSetNaIsFalse() throws Exception {
    LightCellValue result =
        evaluate(
            List.of(field(new ValueMetaInteger("amount"), null)),
            "IF(ISBLANK([amount]), 1, 0)",
            false);

    assertEquals(CellType.NUMERIC, result.getCellType());
    assertEquals(1.0, result.getNumberValue());
  }

  @Test
  void nullFieldIsNaWhenSetNaIsTrue() throws Exception {
    LightCellValue result =
        evaluate(List.of(field(new ValueMetaInteger("amount"), null)), "ISNA([amount])", true);

    assertEquals(CellType.BOOLEAN, result.getCellType());
    assertTrue(result.getBooleanValue());
  }

  @Test
  void dateFieldOnTheExcelEpochIsEvaluated() throws Exception {
    // 1899-12-31 is the oldest representable date: it is Excel date serial number 0.
    LightCellValue result =
        evaluate(List.of(field(new ValueMetaDate("start"), date(1899, 12, 31))), "[start]", false);

    assertEquals(CellType.NUMERIC, result.getCellType());
    assertEquals(0.0, result.getNumberValue());
  }

  @Test
  void dateFieldBeforeTheExcelEpochIsRejected() {
    HopValueException e =
        assertThrows(
            HopValueException.class,
            () ->
                evaluate(
                    List.of(field(new ValueMetaDate("start"), date(1800, 1, 1))),
                    "IF(1=2, DATE(2000,1,1), [start])",
                    false));

    assertTrue(e.getMessage().contains("start"), e.getMessage());
    assertTrue(e.getMessage().contains("1899-12-31"), e.getMessage());
  }

  @Test
  void timestampFieldBeforeTheExcelEpochIsRejected() {
    Timestamp timestamp = new Timestamp(date(1750, 6, 15).getTime());

    HopValueException e =
        assertThrows(
            HopValueException.class,
            () ->
                evaluate(
                    List.of(field(new ValueMetaTimestamp("start"), timestamp)),
                    "[start] + 1",
                    false));

    assertTrue(e.getMessage().contains("start"), e.getMessage());
  }

  @Test
  void nullDateFieldIsNotRejected() throws Exception {
    LightCellValue result =
        evaluate(
            List.of(field(new ValueMetaDate("start"), null)), "IF(ISBLANK([start]), 1, 0)", false);

    assertEquals(CellType.NUMERIC, result.getCellType());
    assertEquals(1.0, result.getNumberValue());
  }

  @Test
  void twoIntegerFieldsAreSummed() throws Exception {
    LightCellValue result =
        evaluate(
            List.of(field(new ValueMetaInteger("a"), 10L), field(new ValueMetaInteger("b"), 20L)),
            "[a] + [b]",
            false);

    assertEquals(CellType.NUMERIC, result.getCellType());
    assertEquals(30.0, result.getNumberValue());
  }

  @Test
  void replaceMapRedirectsFormulaFieldToRealColumn() throws Exception {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaInteger("realAmount"));
    Object[] row = new Object[] {42L};

    HashMap<String, String> replaceMap = new HashMap<>();
    replaceMap.put("aliasAmount", "realAmount");

    String formula = "[aliasAmount] * 2";
    FormulaMetaFunction fn =
        new FormulaMetaFunction("result", formula, IValueMeta.TYPE_INTEGER, -1, -1, "", false);

    LightCellValue result =
        evaluate(new Variables(), rowMeta, row, fn, replaceMap, formula);
    assertEquals(CellType.NUMERIC, result.getCellType());
    assertEquals(84.0, result.getNumberValue());
  }

  @Test
  void variablesAreResolvedBeforeEvaluation() throws Exception {
    Variables variables = new Variables();
    variables.setVariable("THRESHOLD", "50");

    LightCellValue result =
        evaluate(
            variables,
            List.of(field(new ValueMetaInteger("amount"), 60L)),
            "[amount] > ${THRESHOLD}",
            false);

    assertEquals(CellType.BOOLEAN, result.getCellType());
    assertTrue(result.getBooleanValue());
  }

  private LightCellValue evaluate(List<FieldBinding> bindings, String formula, boolean setNa)
      throws Exception {
    return evaluate(new Variables(), bindings, formula, setNa);
  }

  private LightCellValue evaluate(
      Variables variables, List<FieldBinding> bindings, String formula, boolean setNa)
      throws Exception {
    RowMeta rowMeta = new RowMeta();
    Object[] row = new Object[bindings.size()];
    for (int i = 0; i < bindings.size(); i++) {
      FieldBinding binding = bindings.get(i);
      rowMeta.addValueMeta(binding.meta);
      row[i] = binding.value;
    }

    FormulaMetaFunction fn =
        new FormulaMetaFunction("result", formula, IValueMeta.TYPE_STRING, -1, -1, "", setNa);

    return evaluate(variables, rowMeta, row, fn, new HashMap<>(), formula);
  }

  /** Mirrors the resolution, replacement and binding logic of {@link Formula}. */
  private LightCellValue evaluate(
      Variables variables,
      RowMeta rowMeta,
      Object[] row,
      FormulaMetaFunction fn,
      HashMap<String, String> replaceMap,
      String rawFormula)
      throws Exception {
    String formula = variables.resolve(fn.getFormula());
    List<String> fields = getFormulaFieldList(formula);

    boolean replaced = false;
    for (String field : fields) {
      String realFieldName = replaceMap.get(field);
      if (realFieldName != null) {
        formula = formula.replace("[" + field + "]", "[" + realFieldName + "]");
        replaced = true;
      }
    }
    if (replaced) {
      fields = getFormulaFieldList(formula);
    }

    StandaloneFormulaEngine.Builder builder = StandaloneFormulaEngine.newBuilder();
    int[] indexes = new int[fields.size()];
    for (int f = 0; f < fields.size(); f++) {
      builder.input(fields.get(f));
      indexes[f] = rowMeta.indexOfValue(fields.get(f));
    }
    for (int f = 0; f < fields.size(); f++) {
      formula = formula.replace(
          "[" + fields.get(f) + "]", CellReference.convertNumToColString(f) + "1");
    }

    CompiledFormula compiled = builder.build().compile(formula);
    StandaloneFormulaEvaluator evaluator = compiled.newEvaluator();
    bindInputs(evaluator, rowMeta, row, indexes, fn);
    return evaluator.evaluate();
  }

  private void bindInputs(
      StandaloneFormulaEvaluator evaluator,
      RowMeta rowMeta,
      Object[] row,
      int[] indexes,
      FormulaMetaFunction formula)
      throws HopValueException {
    for (int f = 0; f < indexes.length; f++) {
      int position = indexes[f];
      IValueMeta fieldMeta = rowMeta.getValueMeta(position);
      if (row[position] != null) {
        if (fieldMeta.isString()) {
          evaluator.setString(f, rowMeta.getString(row, position));
        } else if (fieldMeta.isBoolean()) {
          evaluator.setBoolean(f, rowMeta.getBoolean(row, position));
        } else if (fieldMeta.isBigNumber()) {
          evaluator.setNumber(f, rowMeta.getNumber(row, position));
        } else if (fieldMeta.isDate()) {
          Date date = rowMeta.getDate(row, position);
          checkSupportedDate(fieldMeta, date);
          evaluator.setDate(f, date);
        } else if (fieldMeta.isInteger()) {
          evaluator.setNumber(f, rowMeta.getInteger(row, position));
        } else if (fieldMeta.isNumber()) {
          evaluator.setNumber(f, rowMeta.getNumber(row, position));
        } else {
          evaluator.setString(f, rowMeta.getString(row, position));
        }
      } else if (formula.isSetNa()) {
        evaluator.setError(f, FormulaError.NA);
      } else {
        evaluator.setBlank(f);
      }
    }
  }

  private void checkSupportedDate(IValueMeta fieldMeta, Date date) throws HopValueException {
    if (date == null || DateUtil.getExcelDate(date) >= 0) {
      return;
    }
    throw new HopValueException(
        "Field ["
            + fieldMeta.getName()
            + "] date "
            + fieldMeta.getString(date)
            + " is before 1899-12-31");
  }

  private static FieldBinding field(IValueMeta meta, Object value) {
    return new FieldBinding(meta, value);
  }

  /** POI converts dates in the default time zone, so build them in the default zone as well. */
  private static Date date(int year, int month, int day) {
    return Date.from(
        LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant());
  }

  private record FieldBinding(IValueMeta meta, Object value) {}
}
