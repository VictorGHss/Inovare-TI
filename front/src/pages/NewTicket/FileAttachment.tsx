// Campo de anexo de arquivos com UI de arrastar/clicar
import type { Dispatch, SetStateAction, ChangeEvent } from 'react';
import { Paperclip } from 'lucide-react';
import { toast } from 'react-toastify';

interface Props {
  files: File[];
  setFiles: Dispatch<SetStateAction<File[]>>;
}

const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

export default function FileAttachment({ files, setFiles }: Props) {
  function handleChange(e: ChangeEvent<HTMLInputElement>) {
    if (e.target.files) {
      const selectedFiles = Array.from(e.target.files);
      const validFiles = selectedFiles.filter((file) => file.size <= MAX_FILE_SIZE_BYTES);
      const oversizedFiles = selectedFiles.filter((file) => file.size > MAX_FILE_SIZE_BYTES);

      oversizedFiles.forEach((file) => {
        toast.error(`Arquivo ${file.name} excede o limite de 5MB.`);
      });

      if (validFiles.length > 0) {
        setFiles((prev) => [...prev, ...validFiles]);
      }
    }
  }

  function removeFile(index: number) {
    setFiles((prev) => prev.filter((_, i) => i !== index));
  }

  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-sm font-medium text-slate-700">Anexos</label>

      <label className="flex flex-col items-center justify-center gap-2 p-4 border-2 border-dashed border-slate-200 rounded-xl cursor-pointer hover:border-primary/50 hover:bg-primary/5 transition-colors">
        <Paperclip size={20} className="text-slate-400" />
        <span className="text-sm text-slate-400 text-center">
          Anexar imagens ou documentos{' '}
          <span className="text-slate-300">(opcional)</span>
        </span>
        <input
          type="file"
          multiple
          className="hidden"
          accept="image/*,.pdf,.doc,.docx"
          onChange={handleChange}
        />
      </label>

      {files.length > 0 && (
        <ul className="flex flex-col gap-1 mt-1">
          {files.map((file, i) => (
            <li key={i} className="flex items-center justify-between text-xs text-slate-600 bg-slate-50 rounded-lg px-3 py-1.5">
              <span className="truncate max-w-[85%]">{file.name}</span>
              <button type="button" onClick={() => removeFile(i)}
                className="text-slate-400 hover:text-red-400 transition-colors ml-2 flex-shrink-0">
                ✕
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
