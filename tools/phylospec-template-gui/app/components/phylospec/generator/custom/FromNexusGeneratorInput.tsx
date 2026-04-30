"use client";

import { useContext, useEffect, useMemo } from "react";
import { TypeSelector, TypeSelectorValue } from "../../TypeSelector";
import { DefaultsContext } from "../../DefaultsContext";
import type { ComponentProps } from "../../types";
import { DEFAULT_COMPONENT_ID } from "../constants";
import type { GeneratorArg, GeneratorInputValue } from "../GeneratorInput";

const LITERAL_STRING_ID = "literal.string";

type FromNexusGeneratorInputProps = ComponentProps<GeneratorInputValue> & {
  args: GeneratorArg[];
  description?: string;
};

export function FromNexusGeneratorInput({
  value,
  onChange,
  args,
  description,
}: FromNexusGeneratorInputProps) {
  const defaults = useContext(DefaultsContext);

  const sentinelDefaults = useMemo(
    () =>
      Object.fromEntries(
        args
          .filter((arg) => arg.name in defaults)
          .map((arg) => [
            arg.name,
            {
              componentId: DEFAULT_COMPONENT_ID,
              value: defaults[arg.name],
              isDistribution: false,
            },
          ]),
      ),
    [args, defaults],
  );

  useEffect(() => {
    if (Object.keys(sentinelDefaults).length === 0) return;
    const needsUpdate = Object.entries(sentinelDefaults).some(
      ([k, v]) =>
        value?.[k]?.componentId !== DEFAULT_COMPONENT_ID ||
        value?.[k]?.value !== v.value,
    );
    if (needsUpdate) onChange({ ...(value ?? {}), ...sentinelDefaults });
  }, [sentinelDefaults]);

  const visibleArgs = args.filter((arg) => !(arg.name in defaults));
  const fileArg = visibleArgs.find((a) => a.name === "file");
  const otherVisibleArgs = visibleArgs.filter((a) => a.name !== "file");

  const fileSelectorValue = value?.file ?? null;
  const displayName =
    fileSelectorValue?.componentId === LITERAL_STRING_ID &&
    typeof fileSelectorValue.value === "string"
      ? fileSelectorValue.value
      : null;

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0];
    if (!f) return;
    onChange({
      ...(value ?? {}),
      ...sentinelDefaults,
      file: {
        componentId: LITERAL_STRING_ID,
        value: f.name,
        isDistribution: false,
      },
    });
  }

  function handleArgChange(argName: string, v: TypeSelectorValue) {
    onChange({ ...(value ?? {}), ...sentinelDefaults, [argName]: v });
  }

  const hasAnyUi =
    fileArg ||
    otherVisibleArgs.length > 0 ||
    Boolean(description);

  if (!hasAnyUi) return null;

  return (
    <div className="flex flex-col gap-4">
      {description && (
        <p className="text-sm text-gray-600 italic dark:text-gray-400">
          {description}
        </p>
      )}
      {fileArg && (
        <div className="flex flex-col gap-1 rounded-lg border border-gray-200 bg-gray-50/50 p-3 dark:bg-gray-800/60">
          <div className="flex items-center gap-1">
            <span className="text-sm font-medium">file</span>
            <span className="text-sm text-gray-500">=</span>
            <span className="text-sm text-gray-500">(required)</span>
          </div>
          <span className="text-sm italic text-gray-600 dark:text-gray-400">
            {fileArg.description}
          </span>
          <input
            type="file"
            accept=".nex,.nexus,.nxs"
            className="text-sm file:mr-2 file:rounded file:border file:border-gray-300 file:bg-white file:px-2 file:py-1 dark:file:border-gray-600 dark:file:bg-gray-800"
            onChange={handleFileChange}
          />
          {displayName && (
            <span className="text-sm text-gray-600 dark:text-gray-400">
              Selected: {displayName}
            </span>
          )}
        </div>
      )}
      {otherVisibleArgs.map((arg) => {
        const argVal = value?.[arg.name] ?? null;
        const operator = argVal ? (argVal.isDistribution ? "~" : "=") : "=";
        return (
          <div
            key={arg.name}
            className="flex flex-col gap-1 rounded-lg border border-gray-200 bg-gray-50/50 p-3 dark:bg-gray-800/60"
          >
            <div className="flex items-center gap-1">
              <span className="text-sm font-medium">{arg.name}</span>
              <span className="text-sm text-gray-500">{operator}</span>
              {!arg.required && (
                <span className="text-sm text-gray-500">(optional)</span>
              )}
            </div>
            <span className="text-sm italic text-gray-600 dark:text-gray-400">
              {arg.description}
            </span>
            <TypeSelector
              type={arg.type}
              value={argVal}
              onChange={(v) => handleArgChange(arg.name, v)}
              allowDistributions={true}
            />
          </div>
        );
      })}
    </div>
  );
}
