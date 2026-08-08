import { useNavigate } from 'react-router-dom'
import { useConfirm } from './useConfirm'

// Returns an onClick for a "Back"/"Cancel" Link that intercepts navigation only while there is
// unsaved input. Returning without preventDefault() lets the Link navigate normally, which keeps the
// clean-form case a plain link (middle-click, open-in-new-tab and all) rather than a JS handler.
export function useDiscardGuard({
  isDirty,
  to,
  title = 'Discard changes?',
  message = 'You have unsaved changes that will be lost.',
}) {
  const navigate = useNavigate()
  const confirm = useConfirm()

  return async function handleClick(e) {
    if (!isDirty) return
    e.preventDefault()
    const ok = await confirm({ title, message, confirmLabel: 'Discard', danger: true })
    if (ok) navigate(to)
  }
}
