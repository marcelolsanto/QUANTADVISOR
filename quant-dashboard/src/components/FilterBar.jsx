
import { theme } from '../theme';

export const FilterBar = ({ setFilter, activeFilter, options }) => {
  const buttonStyle = (isActive, color) => ({
    padding: '6px 14px',
    borderRadius: '6px',
    border: `1px solid ${isActive ? color : theme.border}`,
    backgroundColor: isActive ? color : 'transparent',
    color: isActive ? '#fff' : theme.textMuted,
    fontWeight: 'bold',
    fontSize: '0.85rem',
    cursor: 'pointer',
    transition: 'all 0.2s ease',
  });

  return (
    <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
      {options.map((opt, index) => (
        <button
          key={index}
          style={buttonStyle(activeFilter === opt.value, opt.color)}
          onClick={() => setFilter(opt.value)}
        >
          {opt.label}
        </button>
      ))}
    </div>
  );
};

